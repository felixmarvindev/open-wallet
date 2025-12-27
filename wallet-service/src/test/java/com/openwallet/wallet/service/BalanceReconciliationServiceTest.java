package com.openwallet.wallet.service;

import com.openwallet.wallet.domain.Wallet;
import com.openwallet.wallet.exception.WalletNotFoundException;
import com.openwallet.wallet.repository.LedgerEntryRepository;
import com.openwallet.wallet.repository.WalletRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@SuppressWarnings("null")
class BalanceReconciliationServiceTest {

    @Mock
    private WalletRepository walletRepository;

    @Mock
    private LedgerEntryRepository ledgerEntryRepository;

    @InjectMocks
    private BalanceReconciliationService reconciliationService;

    @Test
    void reconcileBalanceReturnsReconciledResultWhenBalancesMatch() {
        // Given: Wallet with stored balance
        Long walletId = 1L;
        Long customerId = 100L;
        BigDecimal storedBalance = new BigDecimal("1000.00");
        BigDecimal calculatedBalance = new BigDecimal("1000.00");

        Wallet wallet = Wallet.builder()
                .id(walletId)
                .customerId(customerId)
                .currency("KES")
                .balance(storedBalance)
                .build();

        when(walletRepository.findByCustomerIdAndId(customerId, walletId))
                .thenReturn(Optional.of(wallet));
        when(ledgerEntryRepository.calculateBalanceFromLedger(walletId))
                .thenReturn(calculatedBalance);

        // When: Reconcile balance
        BalanceReconciliationService.ReconciliationResult result = 
                reconciliationService.reconcileBalance(walletId, customerId);

        // Then: Should be reconciled
        assertThat(result.isReconciled()).isTrue();
        assertThat(result.getWalletId()).isEqualTo(walletId);
        assertThat(result.getCurrency()).isEqualTo("KES");
        assertThat(result.getStoredBalance()).isEqualByComparingTo(storedBalance);
        assertThat(result.getCalculatedBalance()).isEqualByComparingTo(calculatedBalance);
        assertThat(result.getDiscrepancy()).isEqualByComparingTo(BigDecimal.ZERO);
    }

    @Test
    void reconcileBalanceReturnsDiscrepancyWhenBalancesDontMatch() {
        // Given: Wallet with stored balance different from calculated
        Long walletId = 2L;
        Long customerId = 200L;
        BigDecimal storedBalance = new BigDecimal("1000.00");
        BigDecimal calculatedBalance = new BigDecimal("1050.00");
        BigDecimal expectedDiscrepancy = new BigDecimal("50.00");

        Wallet wallet = Wallet.builder()
                .id(walletId)
                .customerId(customerId)
                .currency("KES")
                .balance(storedBalance)
                .build();

        when(walletRepository.findByCustomerIdAndId(customerId, walletId))
                .thenReturn(Optional.of(wallet));
        when(ledgerEntryRepository.calculateBalanceFromLedger(walletId))
                .thenReturn(calculatedBalance);

        // When: Reconcile balance
        BalanceReconciliationService.ReconciliationResult result = 
                reconciliationService.reconcileBalance(walletId, customerId);

        // Then: Should show discrepancy
        assertThat(result.isReconciled()).isFalse();
        assertThat(result.getStoredBalance()).isEqualByComparingTo(storedBalance);
        assertThat(result.getCalculatedBalance()).isEqualByComparingTo(calculatedBalance);
        assertThat(result.getDiscrepancy()).isEqualByComparingTo(expectedDiscrepancy);
    }

    @Test
    void reconcileBalanceThrowsExceptionWhenWalletNotFound() {
        // Given: Non-existent wallet
        Long walletId = 999L;
        Long customerId = 100L;

        when(walletRepository.findByCustomerIdAndId(customerId, walletId))
                .thenReturn(Optional.empty());

        // When/Then: Should throw exception
        assertThatThrownBy(() -> reconciliationService.reconcileBalance(walletId, customerId))
                .isInstanceOf(WalletNotFoundException.class)
                .hasMessageContaining("Wallet not found");
    }

    @Test
    void reconcileBalanceHandlesNullCalculatedBalance() {
        // Given: Wallet exists but no ledger entries (null returned)
        Long walletId = 3L;
        Long customerId = 300L;
        BigDecimal storedBalance = new BigDecimal("500.00");

        Wallet wallet = Wallet.builder()
                .id(walletId)
                .customerId(customerId)
                .currency("KES")
                .balance(storedBalance)
                .build();

        when(walletRepository.findByCustomerIdAndId(customerId, walletId))
                .thenReturn(Optional.of(wallet));
        when(ledgerEntryRepository.calculateBalanceFromLedger(walletId))
                .thenReturn(null); // No ledger entries

        // When: Reconcile balance
        BalanceReconciliationService.ReconciliationResult result = 
                reconciliationService.reconcileBalance(walletId, customerId);

        // Then: Should treat null as zero
        assertThat(result.getCalculatedBalance()).isEqualByComparingTo(BigDecimal.ZERO);
        assertThat(result.getDiscrepancy()).isEqualByComparingTo(new BigDecimal("-500.00"));
    }

    @Test
    void reconcileBalanceThrowsExceptionWhenWalletIdIsNull() {
        assertThatThrownBy(() -> reconciliationService.reconcileBalance(null, 100L))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Wallet ID is required");
    }

    @Test
    void reconcileBalanceThrowsExceptionWhenCustomerIdIsNull() {
        assertThatThrownBy(() -> reconciliationService.reconcileBalance(1L, null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Customer ID is required");
    }

    @Test
    void reconcileBalanceHandlesNegativeDiscrepancy() {
        // Given: Stored balance is higher than calculated (missing debit in ledger)
        Long walletId = 4L;
        Long customerId = 400L;
        BigDecimal storedBalance = new BigDecimal("1000.00");
        BigDecimal calculatedBalance = new BigDecimal("900.00");
        BigDecimal expectedDiscrepancy = new BigDecimal("-100.00");

        Wallet wallet = Wallet.builder()
                .id(walletId)
                .customerId(customerId)
                .currency("KES")
                .balance(storedBalance)
                .build();

        when(walletRepository.findByCustomerIdAndId(customerId, walletId))
                .thenReturn(Optional.of(wallet));
        when(ledgerEntryRepository.calculateBalanceFromLedger(walletId))
                .thenReturn(calculatedBalance);

        // When: Reconcile balance
        BalanceReconciliationService.ReconciliationResult result = 
                reconciliationService.reconcileBalance(walletId, customerId);

        // Then: Should show negative discrepancy
        assertThat(result.isReconciled()).isFalse();
        assertThat(result.getDiscrepancy()).isEqualByComparingTo(expectedDiscrepancy);
    }
}

