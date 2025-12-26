package com.openwallet.wallet.events;

import com.openwallet.wallet.service.WalletService;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.junit.jupiter.api.extension.ExtendWith;

import java.math.BigDecimal;

import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@SuppressWarnings("null")
class TransactionEventListenerTest {

    @Mock
    private WalletService walletService;

    @InjectMocks
    private TransactionEventListener listener;

    @Test
    void handleDepositUpdatesToWalletBalance() {
        TransactionEvent event = TransactionEvent.builder()
                .transactionId(100L)
                .eventType("TRANSACTION_COMPLETED")
                .transactionType("DEPOSIT")
                .amount(new BigDecimal("100.00"))
                .currency("KES")
                .toWalletId(1L)
                .build();

        listener.handle(event);

        verify(walletService, times(1)).updateBalanceFromTransaction(
                eq(1L),
                eq(new BigDecimal("100.00")),
                eq("DEPOSIT"),
                eq(true) // Credit: increase balance
        );
    }

    @Test
    void handleWithdrawalUpdatesFromWalletBalance() {
        TransactionEvent event = TransactionEvent.builder()
                .transactionId(200L)
                .eventType("TRANSACTION_COMPLETED")
                .transactionType("WITHDRAWAL")
                .amount(new BigDecimal("50.00"))
                .currency("KES")
                .fromWalletId(2L)
                .build();

        listener.handle(event);

        verify(walletService, times(1)).updateBalanceFromTransaction(
                eq(2L),
                eq(new BigDecimal("50.00")),
                eq("WITHDRAWAL"),
                eq(false) // Debit: decrease balance
        );
    }

    @Test
    void handleTransferUpdatesBothWallets() {
        TransactionEvent event = TransactionEvent.builder()
                .transactionId(300L)
                .eventType("TRANSACTION_COMPLETED")
                .transactionType("TRANSFER")
                .amount(new BigDecimal("30.00"))
                .currency("KES")
                .fromWalletId(1L)
                .toWalletId(2L)
                .build();

        listener.handle(event);

        // Verify debit for source wallet
        verify(walletService, times(1)).updateBalanceFromTransaction(
                eq(1L),
                eq(new BigDecimal("30.00")),
                eq("TRANSFER"),
                eq(false) // Debit: decrease balance
        );

        // Verify credit for destination wallet
        verify(walletService, times(1)).updateBalanceFromTransaction(
                eq(2L),
                eq(new BigDecimal("30.00")),
                eq("TRANSFER"),
                eq(true) // Credit: increase balance
        );
    }

    @Test
    void ignoresNonCompletedEvents() {
        TransactionEvent event = TransactionEvent.builder()
                .transactionId(400L)
                .eventType("TRANSACTION_INITIATED")
                .transactionType("DEPOSIT")
                .amount(new BigDecimal("100.00"))
                .toWalletId(1L)
                .build();

        listener.handle(event);

        verify(walletService, never()).updateBalanceFromTransaction(
                any(), any(), any(), anyBoolean());
    }

    @Test
    void handlesNullEvent() {
        listener.handle(null);

        verify(walletService, never()).updateBalanceFromTransaction(
                any(), any(), any(), anyBoolean());
    }

    @Test
    void handlesDepositWithMissingToWalletId() {
        TransactionEvent event = TransactionEvent.builder()
                .transactionId(500L)
                .eventType("TRANSACTION_COMPLETED")
                .transactionType("DEPOSIT")
                .amount(new BigDecimal("100.00"))
                .toWalletId(null)
                .build();

        listener.handle(event);

        verify(walletService, never()).updateBalanceFromTransaction(
                any(), any(), any(), anyBoolean());
    }

    @Test
    void handlesWithdrawalWithMissingFromWalletId() {
        TransactionEvent event = TransactionEvent.builder()
                .transactionId(600L)
                .eventType("TRANSACTION_COMPLETED")
                .transactionType("WITHDRAWAL")
                .amount(new BigDecimal("50.00"))
                .fromWalletId(null)
                .build();

        listener.handle(event);

        verify(walletService, never()).updateBalanceFromTransaction(
                any(), any(), any(), anyBoolean());
    }

    @Test
    void handlesTransferWithSameWalletId() {
        TransactionEvent event = TransactionEvent.builder()
                .transactionId(700L)
                .eventType("TRANSACTION_COMPLETED")
                .transactionType("TRANSFER")
                .amount(new BigDecimal("30.00"))
                .fromWalletId(1L)
                .toWalletId(1L) // Same wallet
                .build();

        listener.handle(event);

        verify(walletService, never()).updateBalanceFromTransaction(
                any(), any(), any(), anyBoolean());
    }

    @Test
    void handlesUnknownTransactionType() {
        TransactionEvent event = TransactionEvent.builder()
                .transactionId(800L)
                .eventType("TRANSACTION_COMPLETED")
                .transactionType("UNKNOWN_TYPE")
                .amount(new BigDecimal("100.00"))
                .toWalletId(1L)
                .build();

        listener.handle(event);

        verify(walletService, never()).updateBalanceFromTransaction(
                any(), any(), any(), anyBoolean());
    }
}


