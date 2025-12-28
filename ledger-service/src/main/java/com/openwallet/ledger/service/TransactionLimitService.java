package com.openwallet.ledger.service;

import com.openwallet.ledger.domain.TransactionType;
import com.openwallet.ledger.dto.WalletLimits;
import com.openwallet.ledger.exception.LimitExceededException;
import com.openwallet.ledger.exception.WalletNotFoundException;
import com.openwallet.ledger.repository.TransactionRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.LocalTime;

/**
 * Service for validating transaction limits before processing transactions.
 * 
 * Validates that a transaction would not exceed the wallet's daily or monthly limits.
 * Limits are retrieved from the wallets table (read-only access).
 * Current usage is calculated from completed transactions in the transactions table.
 */
@Service
@RequiredArgsConstructor
@Slf4j
@SuppressWarnings("null")
public class TransactionLimitService {

    private final TransactionRepository transactionRepository;
    private final WalletLimitsService walletLimitsService;

    /**
     * Validates that a transaction would not exceed wallet limits.
     * 
     * @param walletId Wallet ID (can be fromWalletId or toWalletId depending on transaction type)
     * @param amount Transaction amount
     * @param transactionType Transaction type (DEPOSIT, WITHDRAWAL, TRANSFER)
     * @throws WalletNotFoundException if wallet not found
     * @throws LimitExceededException if transaction would exceed limits
     */
    @Transactional(readOnly = true)
    public void validateTransactionLimits(Long walletId, BigDecimal amount, TransactionType transactionType) {
        if (walletId == null) {
            // Cash account transactions (deposits/withdrawals) don't have wallet limits
            log.debug("Skipping limit validation for cash account transaction");
            return;
        }

        if (amount == null || amount.compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalArgumentException("Amount must be greater than zero");
        }

        log.debug("Validating transaction limits for walletId={}, amount={}, type={}", 
                walletId, amount, transactionType);

        // Get wallet limits
        WalletLimits limits = walletLimitsService.getWalletLimits(walletId)
                .orElseThrow(() -> new WalletNotFoundException("Wallet not found: " + walletId));

        // Calculate current usage
        LocalDateTime now = LocalDateTime.now();
        LocalDateTime startOfDay = now.toLocalDate().atStartOfDay();
        LocalDateTime startOfMonth = now.withDayOfMonth(1).with(LocalTime.MIN);
        // Use now as end time (inclusive) to include all transactions up to current moment
        // Note: Query uses <= so transactions at exact same timestamp are included

        BigDecimal dailyUsage = transactionRepository.sumTransactionAmountsByWalletAndDateRange(
                walletId, startOfDay, now);
        BigDecimal monthlyUsage = transactionRepository.sumTransactionAmountsByWalletAndDateRange(
                walletId, startOfMonth, now);

        log.debug("Current usage for walletId={}: daily={}, monthly={}, limits: daily={}, monthly={}", 
                walletId, dailyUsage, monthlyUsage, limits.getDailyLimit(), limits.getMonthlyLimit());

        // Check daily limit
        BigDecimal newDailyUsage = dailyUsage.add(amount);
        if (newDailyUsage.compareTo(limits.getDailyLimit()) > 0) {
            log.warn("Daily limit exceeded for walletId={}: current={}, limit={}, requested={}, would result={}", 
                    walletId, dailyUsage, limits.getDailyLimit(), amount, newDailyUsage);
            throw new LimitExceededException(walletId, "DAILY", dailyUsage, limits.getDailyLimit(), amount);
        }

        // Check monthly limit
        BigDecimal newMonthlyUsage = monthlyUsage.add(amount);
        if (newMonthlyUsage.compareTo(limits.getMonthlyLimit()) > 0) {
            log.warn("Monthly limit exceeded for walletId={}: current={}, limit={}, requested={}, would result={}", 
                    walletId, monthlyUsage, limits.getMonthlyLimit(), amount, newMonthlyUsage);
            throw new LimitExceededException(walletId, "MONTHLY", monthlyUsage, limits.getMonthlyLimit(), amount);
        }

        log.debug("Transaction limits validated successfully for walletId={}", walletId);
    }
}

