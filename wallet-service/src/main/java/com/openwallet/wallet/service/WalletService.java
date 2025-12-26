package com.openwallet.wallet.service;

import com.openwallet.wallet.cache.BalanceCacheService;
import com.openwallet.wallet.cache.BalanceCacheService.BalanceSnapshot;
import com.openwallet.wallet.domain.Wallet;
import com.openwallet.wallet.dto.BalanceResponse;
import com.openwallet.wallet.dto.CreateWalletRequest;
import com.openwallet.wallet.dto.WalletResponse;
import com.openwallet.wallet.exception.WalletAlreadyExistsException;
import com.openwallet.wallet.exception.WalletNotFoundException;
import com.openwallet.wallet.repository.WalletRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Locale;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
@SuppressWarnings("null")
public class WalletService {

    private final WalletRepository walletRepository;
    private final BalanceCacheService balanceCacheService;

    @Transactional
    public WalletResponse createWallet(Long customerId, CreateWalletRequest request) {
        if (customerId == null) {
            throw new IllegalArgumentException("Customer ID is required");
        }
        if (request == null || !StringUtils.hasText(request.getCurrency())) {
            throw new IllegalArgumentException("Currency is required");
        }

        String currency = request.getCurrency().toUpperCase(Locale.ROOT);
        walletRepository.findByCustomerIdAndCurrency(customerId, currency).ifPresent(existing -> {
            throw new WalletAlreadyExistsException("Wallet already exists for currency: " + currency);
        });

        Wallet wallet = Wallet.builder()
                .customerId(customerId)
                .currency(currency)
                .dailyLimit(defaultLimit(request.getDailyLimit(), new BigDecimal("100000.00")))
                .monthlyLimit(defaultLimit(request.getMonthlyLimit(), new BigDecimal("1000000.00")))
                .build();

        Wallet saved = walletRepository.save(wallet);
        return toResponse(saved);
    }

    @Transactional(readOnly = true)
    public WalletResponse getWallet(Long walletId, Long customerId) {
        return walletRepository.findByCustomerIdAndId(customerId, walletId)
                .map(wallet -> {
                    WalletResponse response = toResponse(wallet);
                    cacheBalance(wallet, response);
                    return response;
                })
                .orElseThrow(() -> new WalletNotFoundException("Wallet not found"));
    }

    @Transactional(readOnly = true)
    public List<WalletResponse> getMyWallets(Long customerId) {
        return walletRepository.findByCustomerId(customerId)
                .stream()
                .map(wallet -> {
                    WalletResponse response = toResponse(wallet);
                    cacheBalance(wallet, response);
                    return response;
                })
                .collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public BalanceResponse getWalletBalance(Long walletId, Long customerId) {
        return balanceCacheService.getBalance(walletId)
                .map(snapshot -> BalanceResponse.builder()
                        .balance(new BigDecimal(snapshot.getBalance()))
                        .currency(snapshot.getCurrency())
                        .lastUpdated(snapshot.getUpdatedAt())
                        .build())
                .orElseGet(() -> walletRepository.findByCustomerIdAndId(customerId, walletId)
                        .map(wallet -> {
                            BalanceResponse response = BalanceResponse.builder()
                                    .balance(wallet.getBalance())
                                    .currency(wallet.getCurrency())
                                    .lastUpdated(wallet.getUpdatedAt() != null ? wallet.getUpdatedAt().toString()
                                            : LocalDateTime.now().toString())
                                    .build();
                            cacheBalance(wallet, toResponse(wallet));
                            return response;
                        })
                        .orElseThrow(() -> new WalletNotFoundException("Wallet not found")));
    }

    /**
     * Creates or retrieves a wallet automatically from customer creation event.
     * This method is idempotent: if a wallet already exists for the customer, it returns
     * the existing wallet. This ensures safe event reprocessing and handles race conditions.
     * 
     * Uses default limits for KYC-pending customers.
     * This is used by event listeners to create wallets automatically.
     *
     * @param customerId Customer ID from the event
     * @return Created or existing wallet (idempotent)
     */
    @Transactional
    public Wallet createWalletFromEvent(Long customerId) {
        if (customerId == null) {
            throw new IllegalArgumentException("Customer ID is required");
        }

        // Idempotent: return existing wallet if found
        List<Wallet> existingWallets = walletRepository.findByCustomerId(customerId);
        if (!existingWallets.isEmpty()) {
            Wallet existing = existingWallets.get(0);
            // Ensure balance is cached
            cacheBalance(existing, toResponse(existing));
            return existing;
        }

        // Create wallet with initial low limits (KYC pending)
        Wallet wallet = Wallet.builder()
                .customerId(customerId)
                .currency("KES")  // Default currency
                .balance(BigDecimal.ZERO)
                .dailyLimit(new BigDecimal("5000.00"))    // Low limit (KYC pending)
                .monthlyLimit(new BigDecimal("20000.00")) // Low limit (KYC pending)
                .build();

        Wallet saved = walletRepository.save(wallet);
        
        // Cache the initial balance
        cacheBalance(saved, toResponse(saved));
        
        return saved;
    }

    /**
     * Updates wallet balance from a completed transaction.
     * Called by TransactionEventListener when TRANSACTION_COMPLETED event is received.
     * 
     * This method atomically updates the wallet balance based on the transaction type:
     * - DEPOSIT: Increases balance (toWalletId)
     * - WITHDRAWAL: Decreases balance (fromWalletId)
     * - TRANSFER: Decreases balance (fromWalletId) and increases balance (toWalletId)
     * 
     * After updating the database, the cache is invalidated and refreshed with the new balance.
     *
     * @param walletId        Wallet ID to update
     * @param amount          Transaction amount
     * @param transactionType Transaction type (DEPOSIT, WITHDRAWAL, TRANSFER)
     * @param isCredit        true if this is a credit (increase balance), false if debit (decrease balance)
     * @throws WalletNotFoundException if wallet not found
     * @throws IllegalArgumentException if balance would become negative
     */
    @Transactional
    public void updateBalanceFromTransaction(Long walletId, BigDecimal amount, String transactionType, boolean isCredit) {
        if (walletId == null) {
            throw new IllegalArgumentException("Wallet ID is required");
        }
        if (amount == null || amount.compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalArgumentException("Amount must be greater than zero");
        }
        if (transactionType == null) {
            throw new IllegalArgumentException("Transaction type is required");
        }

        log.info("Updating balance for walletId={}, amount={}, type={}, isCredit={}", 
                walletId, amount, transactionType, isCredit);

        Wallet wallet = walletRepository.findById(walletId)
                .orElseThrow(() -> new WalletNotFoundException("Wallet not found: " + walletId));

        BigDecimal currentBalance = wallet.getBalance();
        BigDecimal newBalance;

        if (isCredit) {
            // Credit: increase balance (DEPOSIT to wallet, TRANSFER to wallet)
            newBalance = currentBalance.add(amount);
        } else {
            // Debit: decrease balance (WITHDRAWAL from wallet, TRANSFER from wallet)
            newBalance = currentBalance.subtract(amount);
            
            // Validate balance doesn't go negative
            if (newBalance.compareTo(BigDecimal.ZERO) < 0) {
                log.warn("Insufficient balance for walletId={}: current={}, requested={}, would result={}", 
                        walletId, currentBalance, amount, newBalance);
                throw new IllegalArgumentException(
                        String.format("Insufficient balance. Current: %s, Requested: %s", currentBalance, amount));
            }
        }

        // Update wallet balance
        wallet.setBalance(newBalance);
        wallet.setUpdatedAt(LocalDateTime.now());
        Wallet saved = walletRepository.save(wallet);

        log.info("Balance updated for walletId={}: {} → {} (change: {}{})", 
                walletId, currentBalance, newBalance, isCredit ? "+" : "-", amount);

        // Refresh cache with new balance
        try {
            WalletResponse response = toResponse(saved);
            cacheBalance(saved, response);
            log.debug("Refreshed balance cache for walletId={}", walletId);
        } catch (Exception e) {
            log.warn("Failed to refresh cache for walletId={}: {}", walletId, e.getMessage());
            // Don't fail the transaction if cache update fails
        }
    }

    /**
     * Updates wallet transaction limits after KYC verification.
     * Called by KycEventListener when KYC_VERIFIED event is received.
     *
     * @param customerId Customer ID
     * @param kycStatus  KYC status (VERIFIED, REJECTED, etc.)
     * @throws IllegalStateException if no wallet exists for customer
     */
    @Transactional
    public void updateLimitsAfterKyc(Long customerId, String kycStatus) {
        if (customerId == null) {
            throw new IllegalArgumentException("Customer ID is required");
        }

        log.info("Updating wallet limits for customerId={}, kycStatus={}", customerId, kycStatus);

        List<Wallet> wallets = walletRepository.findByCustomerId(customerId);
        if (wallets.isEmpty()) {
            throw new IllegalStateException("No wallet found for customerId: " + customerId);
        }

        // Determine new limits based on KYC status
        BigDecimal newDailyLimit;
        BigDecimal newMonthlyLimit;

        if ("VERIFIED".equals(kycStatus)) {
            // KYC verified → High limits
            newDailyLimit = new BigDecimal("50000.00");    // 50K KES daily
            newMonthlyLimit = new BigDecimal("200000.00"); // 200K KES monthly
            log.info("KYC VERIFIED: Applying high limits ({}D, {}M)", newDailyLimit, newMonthlyLimit);
        } else {
            // KYC rejected or other status → Keep low limits
            newDailyLimit = new BigDecimal("5000.00");     // 5K KES daily
            newMonthlyLimit = new BigDecimal("20000.00");  // 20K KES monthly
            log.info("KYC NOT VERIFIED: Keeping low limits ({}D, {}M)", newDailyLimit, newMonthlyLimit);
        }

        // Update all wallets for this customer
        for (Wallet wallet : wallets) {
            wallet.setDailyLimit(newDailyLimit);
            wallet.setMonthlyLimit(newMonthlyLimit);
            wallet.setUpdatedAt(LocalDateTime.now());
            walletRepository.save(wallet);

            log.info("Updated wallet: walletId={}, currency={}, dailyLimit={}, monthlyLimit={}",
                    wallet.getId(), wallet.getCurrency(), newDailyLimit, newMonthlyLimit);

            // Update cache gracefully
            try {
                WalletResponse response = toResponse(wallet);
                cacheBalance(wallet, response);
                log.debug("Refreshed balance cache for walletId={}", wallet.getId());
            } catch (Exception e) {
                log.warn("Failed to refresh cache for walletId={}: {}", wallet.getId(), e.getMessage());
            }
        }

        log.info("✓ Successfully updated limits for {} wallet(s), customerId={}", wallets.size(), customerId);
    }

    private WalletResponse toResponse(Wallet wallet) {
        return WalletResponse.builder()
                .id(wallet.getId())
                .customerId(wallet.getCustomerId())
                .currency(wallet.getCurrency())
                .status(wallet.getStatus().name())
                .balance(wallet.getBalance())
                .dailyLimit(wallet.getDailyLimit())
                .monthlyLimit(wallet.getMonthlyLimit())
                .createdAt(wallet.getCreatedAt())
                .updatedAt(wallet.getUpdatedAt())
                .build();
    }

    private BigDecimal defaultLimit(BigDecimal provided, BigDecimal fallback) {
        return provided != null ? provided : fallback;
    }

    /**
     * Cache balance for performance optimization.
     * Fails gracefully if Redis is unavailable - caching is optional.
     */
    private void cacheBalance(Wallet wallet, WalletResponse response) {
        try {
            BalanceSnapshot snapshot = new BalanceSnapshot(
                    response.getBalance().toPlainString(),
                    response.getCurrency(),
                    response.getUpdatedAt() != null ? response.getUpdatedAt().toString() : null);
            balanceCacheService.putBalance(wallet.getId(), snapshot);
        } catch (Exception e) {
            // Log but don't fail - caching is optional/best-effort
            // This allows operation to succeed even if Redis is down
            log.warn("Failed to cache balance for walletId={}: {}", wallet.getId(), e.getMessage());
        }
    }
}


