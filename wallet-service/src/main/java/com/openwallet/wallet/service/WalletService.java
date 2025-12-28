package com.openwallet.wallet.service;

import com.openwallet.wallet.cache.BalanceCacheService;
import com.openwallet.wallet.cache.BalanceCacheService.BalanceSnapshot;
import com.openwallet.wallet.domain.Transaction;
import com.openwallet.wallet.domain.Wallet;
import com.openwallet.wallet.domain.WalletStatus;
import com.openwallet.wallet.dto.BalanceResponse;
import com.openwallet.wallet.dto.CreateWalletRequest;
import com.openwallet.wallet.dto.TransactionListResponse;
import com.openwallet.wallet.dto.WalletResponse;
import com.openwallet.wallet.events.WalletEvent;
import com.openwallet.wallet.events.WalletEventProducer;
import com.openwallet.wallet.exception.InsufficientBalanceException;
import com.openwallet.wallet.exception.WalletNotFoundException;
import com.openwallet.wallet.repository.TransactionRepository;
import com.openwallet.wallet.repository.WalletRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
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
    private final TransactionRepository transactionRepository;
    private final WalletEventProducer walletEventProducer;

    @Transactional
    public WalletResponse createWallet(Long customerId, CreateWalletRequest request) {
        if (customerId == null) {
            throw new IllegalArgumentException("Customer ID is required");
        }
        if (request == null) {
            throw new IllegalArgumentException("Request is required");
        }

        // For MVP: Only KES is supported
        // Default to KES if not provided, or validate it's KES if provided
        String currency = "KES";
        if (StringUtils.hasText(request.getCurrency())) {
            currency = request.getCurrency().toUpperCase(Locale.ROOT);
            if (!"KES".equals(currency)) {
                throw new IllegalArgumentException("Only KES currency is supported in MVP. Provided: " + currency);
            }
        }

        // Customers can now have multiple wallets with the same currency (KES)
        // No duplicate check needed - the unique constraint has been removed
        
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

    @Transactional(readOnly = true)
    public TransactionListResponse getWalletTransactions(
            Long walletId,
            Long customerId,
            LocalDateTime fromDate,
            LocalDateTime toDate,
            String status,
            String transactionType,
            Integer page,
            Integer size,
            String sortBy,
            String sortDirection
    ) {
        // Validate wallet ownership
        walletRepository.findByCustomerIdAndId(customerId, walletId)
                .orElseThrow(() -> new WalletNotFoundException("Wallet not found"));

        // Default pagination values
        int pageNumber = (page != null && page >= 0) ? page : 0;
        // Default to 20 if null or <= 0, cap at 100 if > 100
        int pageSize = (size == null || size <= 0) ? 20 : Math.min(size, 100);

        // Default sorting: by initiatedAt descending (newest first)
        Sort sort = Sort.by(Sort.Direction.DESC, "initiatedAt");
        if (sortBy != null && !sortBy.trim().isEmpty()) {
            Sort.Direction direction = "asc".equalsIgnoreCase(sortDirection) 
                    ? Sort.Direction.ASC 
                    : Sort.Direction.DESC;
            
            // Validate sort field
            String validSortBy = validateSortField(sortBy);
            sort = Sort.by(direction, validSortBy);
        }

        Pageable pageable = PageRequest.of(pageNumber, pageSize, sort);

        // Provide default dates to avoid PostgreSQL type inference issues with null parameters
        // Use reasonable date bounds that PostgreSQL can handle (not MIN/MAX which are out of range)
        LocalDateTime effectiveFromDate = fromDate != null ? fromDate : LocalDateTime.of(1900, 1, 1, 0, 0);
        LocalDateTime effectiveToDate = toDate != null ? toDate : LocalDateTime.of(2100, 12, 31, 23, 59, 59);

        // Query transactions directly from database (read-only access)
        Page<Transaction> transactionPage = transactionRepository.findTransactionsWithFilters(
                walletId,
                effectiveFromDate,
                effectiveToDate,
                status,
                transactionType,
                pageable
        );

        // Convert to response
        List<TransactionListResponse.TransactionItem> transactionItems = transactionPage.getContent().stream()
                .map(this::toTransactionItem)
                .collect(Collectors.toList());

        // Build pagination metadata
        TransactionListResponse.PaginationMetadata pagination = TransactionListResponse.PaginationMetadata.builder()
                .page(transactionPage.getNumber())
                .size(transactionPage.getSize())
                .totalElements(transactionPage.getTotalElements())
                .totalPages(transactionPage.getTotalPages())
                .hasNext(transactionPage.hasNext())
                .hasPrevious(transactionPage.hasPrevious())
                .build();

        return TransactionListResponse.builder()
                .transactions(transactionItems)
                .pagination(pagination)
                .build();
    }

    /**
     * Validates and normalizes sort field name.
     * Only allows sorting by safe fields to prevent SQL injection.
     */
    private String validateSortField(String sortBy) {
        String normalized = sortBy.trim().toLowerCase();
        // Allowed sort fields
        switch (normalized) {
            case "id":
            case "initiatedat":
            case "completedat":
            case "amount":
            case "status":
            case "transactiontype":
                return normalized.equals("initiatedat") ? "initiatedAt" :
                       normalized.equals("completedat") ? "completedAt" :
                       normalized.equals("transactiontype") ? "transactionType" :
                       normalized;
            default:
                log.warn("Invalid sort field '{}', defaulting to 'initiatedAt'", sortBy);
                return "initiatedAt";
        }
    }

    /**
     * Converts Transaction entity to TransactionItem DTO.
     */
    private TransactionListResponse.TransactionItem toTransactionItem(Transaction tx) {
        return TransactionListResponse.TransactionItem.builder()
                .id(tx.getId())
                .transactionType(tx.getTransactionType())
                .status(tx.getStatus())
                .amount(tx.getAmount())
                .currency(tx.getCurrency())
                .fromWalletId(tx.getFromWalletId())
                .toWalletId(tx.getToWalletId())
                .initiatedAt(tx.getInitiatedAt() != null ? tx.getInitiatedAt().toString() : null)
                .completedAt(tx.getCompletedAt() != null ? tx.getCompletedAt().toString() : null)
                .failureReason(tx.getFailureReason())
                .build();
    }

    /**
     * Suspends a wallet, preventing transactions.
     * Only the wallet owner or an admin can suspend a wallet.
     *
     * @param walletId Wallet ID to suspend
     * @param customerId Customer ID (for ownership validation)
     * @return Updated wallet response
     * @throws WalletNotFoundException if wallet not found
     * @throws IllegalStateException if wallet is already suspended or closed
     */
    @Transactional
    public WalletResponse suspendWallet(Long walletId, Long customerId) {
        Wallet wallet = walletRepository.findByCustomerIdAndId(customerId, walletId)
                .orElseThrow(() -> new WalletNotFoundException("Wallet not found"));

        if (wallet.getStatus() == WalletStatus.SUSPENDED) {
            throw new IllegalStateException("Wallet is already suspended");
        }

        if (wallet.getStatus() == WalletStatus.CLOSED) {
            throw new IllegalStateException("Cannot suspend a closed wallet");
        }

        WalletStatus previousStatus = wallet.getStatus();
        wallet.setStatus(WalletStatus.SUSPENDED);
        Wallet saved = walletRepository.save(wallet);

        log.info("Wallet suspended: walletId={}, customerId={}, previousStatus={}", 
                walletId, customerId, previousStatus);

        // Publish WALLET_SUSPENDED event
        WalletEvent event = WalletEvent.builder()
                .walletId(saved.getId())
                .customerId(saved.getCustomerId())
                .eventType("WALLET_SUSPENDED")
                .balance(saved.getBalance())
                .currency(saved.getCurrency())
                .timestamp(LocalDateTime.now())
                .build();
        walletEventProducer.publish(event);

        return toResponse(saved);
    }

    /**
     * Activates a suspended wallet, allowing transactions again.
     * Only the wallet owner or an admin can activate a wallet.
     *
     * @param walletId Wallet ID to activate
     * @param customerId Customer ID (for ownership validation)
     * @return Updated wallet response
     * @throws WalletNotFoundException if wallet not found
     * @throws IllegalStateException if wallet is not suspended
     */
    @Transactional
    public WalletResponse activateWallet(Long walletId, Long customerId) {
        Wallet wallet = walletRepository.findByCustomerIdAndId(customerId, walletId)
                .orElseThrow(() -> new WalletNotFoundException("Wallet not found"));

        if (wallet.getStatus() != WalletStatus.SUSPENDED) {
            throw new IllegalStateException("Wallet is not suspended. Current status: " + wallet.getStatus());
        }

        wallet.setStatus(WalletStatus.ACTIVE);
        Wallet saved = walletRepository.save(wallet);

        log.info("Wallet activated: walletId={}, customerId={}", walletId, customerId);

        // Publish WALLET_ACTIVATED event
        WalletEvent event = WalletEvent.builder()
                .walletId(saved.getId())
                .customerId(saved.getCustomerId())
                .eventType("WALLET_ACTIVATED")
                .balance(saved.getBalance())
                .currency(saved.getCurrency())
                .timestamp(LocalDateTime.now())
                .build();
        walletEventProducer.publish(event);

        return toResponse(saved);
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
     * Uses pessimistic locking to prevent concurrent update issues.
     * Implements retry logic for transient database failures.
     * After updating the database, the cache is invalidated and refreshed with the new balance.
     *
     * @param walletId        Wallet ID to update
     * @param amount          Transaction amount
     * @param transactionType Transaction type (DEPOSIT, WITHDRAWAL, TRANSFER)
     * @param isCredit        true if this is a credit (increase balance), false if debit (decrease balance)
     * @throws WalletNotFoundException if wallet not found
     * @throws InsufficientBalanceException if balance would become negative
     * @throws IllegalArgumentException if invalid input
     */
    @Transactional
    public void updateBalanceFromTransaction(Long walletId, BigDecimal amount, String transactionType, boolean isCredit) {
        // Input validation
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

        // Retry logic for transient database failures
        int maxRetries = 3;
        int retryDelayMs = 100;
        Exception lastException = null;

        for (int attempt = 1; attempt <= maxRetries; attempt++) {
            try {
                // Use pessimistic lock to prevent concurrent updates
                Wallet wallet = walletRepository.findByIdWithLock(walletId)
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
                        throw new InsufficientBalanceException(walletId, currentBalance, amount);
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

                // Success - return early
                return;

            } catch (WalletNotFoundException | InsufficientBalanceException | IllegalArgumentException e) {
                // Don't retry for business logic errors
                throw e;
            } catch (org.springframework.dao.DataAccessException e) {
                // Transient database error - retry
                lastException = e;
                if (attempt < maxRetries) {
                    log.warn("Transient database error updating balance for walletId={}, attempt {}/{}: {}", 
                            walletId, attempt, maxRetries, e.getMessage());
                    try {
                        Thread.sleep(retryDelayMs * attempt); // Exponential backoff
                    } catch (InterruptedException ie) {
                        Thread.currentThread().interrupt();
                        throw new RuntimeException("Interrupted during retry", ie);
                    }
                } else {
                    log.error("Failed to update balance for walletId={} after {} attempts", walletId, maxRetries, e);
                }
            } catch (Exception e) {
                // Unexpected error - don't retry
                log.error("Unexpected error updating balance for walletId={}: {}", walletId, e.getMessage(), e);
                throw new RuntimeException("Failed to update wallet balance", e);
            }
        }

        // If we get here, all retries failed
        throw new RuntimeException(
                String.format("Failed to update balance for walletId=%d after %d attempts", walletId, maxRetries),
                lastException);
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


