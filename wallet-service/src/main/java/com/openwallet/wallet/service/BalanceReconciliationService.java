package com.openwallet.wallet.service;

import com.openwallet.wallet.domain.Wallet;
import com.openwallet.wallet.exception.WalletNotFoundException;
import com.openwallet.wallet.repository.LedgerEntryRepository;
import com.openwallet.wallet.repository.WalletRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;

/**
 * Service for balance reconciliation.
 * Compares stored wallet balance with balance calculated from ledger entries.
 * 
 * Uses direct database access to ledger_entries table (read-only) since both services
 * share the same database. This is more efficient than HTTP calls and maintains
 * service boundaries (read-only access).
 */
@Service
@RequiredArgsConstructor
@Slf4j
@SuppressWarnings("null")
public class BalanceReconciliationService {

    private final WalletRepository walletRepository;
    private final LedgerEntryRepository ledgerEntryRepository;

    /**
     * Reconciles wallet balance by comparing stored balance with balance calculated from ledger.
     * 
     * @param walletId Wallet ID
     * @param customerId Customer ID (for authorization)
     * @return Reconciliation result with stored balance, calculated balance, and discrepancy
     * @throws WalletNotFoundException if wallet not found
     */
    @Transactional(readOnly = true)
    public ReconciliationResult reconcileBalance(Long walletId, Long customerId) {
        if (walletId == null) {
            throw new IllegalArgumentException("Wallet ID is required");
        }
        if (customerId == null) {
            throw new IllegalArgumentException("Customer ID is required");
        }

        log.info("Starting balance reconciliation for walletId={}, customerId={}", walletId, customerId);

        // Get stored balance from wallet
        Wallet wallet = walletRepository.findByCustomerIdAndId(customerId, walletId)
                .orElseThrow(() -> new WalletNotFoundException("Wallet not found"));

        BigDecimal storedBalance = wallet.getBalance();
        log.debug("Stored balance for walletId={}: {}", walletId, storedBalance);

        // Calculate balance from ledger entries (direct database query - read-only)
        BigDecimal calculatedBalance = ledgerEntryRepository.calculateBalanceFromLedger(walletId);
        if (calculatedBalance == null) {
            calculatedBalance = BigDecimal.ZERO;
        }
        log.debug("Calculated balance from ledger for walletId={}: {}", walletId, calculatedBalance);

        // Calculate discrepancy
        BigDecimal discrepancy = calculatedBalance.subtract(storedBalance);
        boolean isReconciled = discrepancy.compareTo(BigDecimal.ZERO) == 0;

        log.info("Balance reconciliation for walletId={}: stored={}, calculated={}, discrepancy={}, reconciled={}", 
                walletId, storedBalance, calculatedBalance, discrepancy, isReconciled);

        if (!isReconciled) {
            log.warn("Balance discrepancy detected for walletId={}: stored={}, calculated={}, discrepancy={}", 
                    walletId, storedBalance, calculatedBalance, discrepancy);
        }

        return ReconciliationResult.builder()
                .walletId(walletId)
                .currency(wallet.getCurrency())
                .storedBalance(storedBalance)
                .calculatedBalance(calculatedBalance)
                .discrepancy(discrepancy)
                .isReconciled(isReconciled)
                .build();
    }

    /**
     * Reconciliation result containing stored balance, calculated balance, and discrepancy.
     */
    @lombok.Data
    @lombok.Builder
    public static class ReconciliationResult {
        private Long walletId;
        private String currency;
        private BigDecimal storedBalance;
        private BigDecimal calculatedBalance;
        private BigDecimal discrepancy;
        private boolean isReconciled;
    }

    /**
     * Exception thrown when reconciliation fails.
     */
    public static class ReconciliationException extends RuntimeException {
        public ReconciliationException(String message) {
            super(message);
        }

        public ReconciliationException(String message, Throwable cause) {
            super(message, cause);
        }
    }
}

