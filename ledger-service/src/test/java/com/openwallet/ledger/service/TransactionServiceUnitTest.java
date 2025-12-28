package com.openwallet.ledger.service;

import com.openwallet.ledger.domain.EntryType;
import com.openwallet.ledger.domain.LedgerEntry;
import com.openwallet.ledger.domain.Transaction;
import com.openwallet.ledger.domain.TransactionStatus;
import com.openwallet.ledger.domain.TransactionType;
import com.openwallet.ledger.dto.DepositRequest;
import com.openwallet.ledger.dto.TransactionResponse;
import com.openwallet.ledger.dto.TransferRequest;
import com.openwallet.ledger.dto.WithdrawalRequest;
import com.openwallet.ledger.events.TransactionEvent;
import com.openwallet.ledger.events.TransactionEventProducer;
import com.openwallet.ledger.exception.TransactionNotFoundException;
import com.openwallet.ledger.repository.LedgerEntryRepository;
import com.openwallet.ledger.repository.TransactionRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("TransactionService Unit Tests")
@SuppressWarnings({"ConstantConditions", "null"})
class TransactionServiceUnitTest {

    @Mock
    private TransactionRepository transactionRepository;

    @Mock
    private LedgerEntryRepository ledgerEntryRepository;

    @Mock
    private TransactionEventProducer transactionEventProducer;

    @InjectMocks
    private TransactionService transactionService;

    private Transaction savedTransaction;

    @BeforeEach
    void setUp() {
        savedTransaction = Transaction.builder()
                .id(1L)
                .transactionType(TransactionType.DEPOSIT)
                .amount(new BigDecimal("100.00"))
                .currency("KES")
                .toWalletId(20L)
                .status(TransactionStatus.PENDING)
                .idempotencyKey("test-key")
                .initiatedAt(LocalDateTime.now())
                .build();
    }

    // ========== Deposit Tests ==========

    @Test
    @DisplayName("createDeposit should create transaction and ledger entries successfully")
    void createDepositShouldCreateTransactionAndEntries() {
        // Given
        DepositRequest request = DepositRequest.builder()
                .toWalletId(20L)
                .amount(new BigDecimal("100.00"))
                .currency("KES")
                .idempotencyKey("dep-1")
                .build();

        when(transactionRepository.findByIdempotencyKey("dep-1")).thenReturn(Optional.empty());
        when(transactionRepository.save(any(Transaction.class))).thenReturn(savedTransaction);
        when(ledgerEntryRepository.save(any(LedgerEntry.class))).thenAnswer(invocation -> invocation.getArgument(0));

        // When
        TransactionResponse response = transactionService.createDeposit(request);

        // Then
        assertThat(response).isNotNull();
        assertThat(response.getId()).isEqualTo(1L);
        assertThat(response.getStatus()).isEqualTo("COMPLETED");

        // Verify transaction was saved twice (initial save and status update)
        verify(transactionRepository, times(2)).save(any(Transaction.class));

        // Verify ledger entries were created (DEBIT CASH_ACCOUNT, CREDIT WALLET)
        verify(ledgerEntryRepository, times(2)).save(any(LedgerEntry.class));

        // Verify events were published
        verify(transactionEventProducer, times(1)).publish(argThat(e -> "TRANSACTION_INITIATED".equals(e.getEventType())));
        verify(transactionEventProducer, times(1)).publish(argThat(e -> "TRANSACTION_COMPLETED".equals(e.getEventType())));
    }

    @Test
    @DisplayName("createDeposit should return existing transaction for duplicate idempotency key")
    void createDepositShouldReturnExistingForDuplicateIdempotencyKey() {
        // Given
        DepositRequest request = DepositRequest.builder()
                .toWalletId(20L)
                .amount(new BigDecimal("100.00"))
                .currency("KES")
                .idempotencyKey("dep-existing")
                .build();

        Transaction existing = Transaction.builder()
                .id(99L)
                .transactionType(TransactionType.DEPOSIT)
                .status(TransactionStatus.COMPLETED)
                .build();

        when(transactionRepository.findByIdempotencyKey("dep-existing")).thenReturn(Optional.of(existing));

        // When
        TransactionResponse response = transactionService.createDeposit(request);

        // Then
        assertThat(response.getId()).isEqualTo(99L);
        verify(transactionRepository, never()).save(any(Transaction.class));
        verify(ledgerEntryRepository, never()).save(any(LedgerEntry.class));
    }

    @Test
    @DisplayName("createDeposit should throw exception when toWalletId is null")
    void createDepositShouldThrowWhenToWalletIdIsNull() {
        // Given
        DepositRequest request = DepositRequest.builder()
                .toWalletId(null)
                .amount(new BigDecimal("100.00"))
                .currency("KES")
                .idempotencyKey("dep-null")
                .build();

        // When/Then - Validation happens before repository call, so no stubbing needed
        assertThatThrownBy(() -> transactionService.createDeposit(request))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("toWalletId is required for deposit");

        verify(transactionRepository, never()).findByIdempotencyKey(anyString());
        verify(transactionRepository, never()).save(any(Transaction.class));
    }

    @Test
    @DisplayName("createDeposit should set status to FAILED when ledger entry creation fails")
    void createDepositShouldSetFailedStatusWhenLedgerEntryCreationFails() {
        // Given
        DepositRequest request = DepositRequest.builder()
                .toWalletId(20L)
                .amount(new BigDecimal("100.00"))
                .currency("KES")
                .idempotencyKey("dep-fail")
                .build();

        when(transactionRepository.findByIdempotencyKey("dep-fail")).thenReturn(Optional.empty());
        when(transactionRepository.save(any(Transaction.class))).thenReturn(savedTransaction);
        when(ledgerEntryRepository.save(any(LedgerEntry.class)))
                .thenThrow(new RuntimeException("Database error"));

        // When/Then
        assertThatThrownBy(() -> transactionService.createDeposit(request))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Failed to complete deposit transaction");

        // Verify transaction was saved with FAILED status
        ArgumentCaptor<Transaction> transactionCaptor = ArgumentCaptor.forClass(Transaction.class);
        verify(transactionRepository, atLeast(2)).save(transactionCaptor.capture());
        
        Transaction failedTransaction = transactionCaptor.getAllValues().stream()
                .filter(t -> t.getStatus() == TransactionStatus.FAILED)
                .findFirst()
                .orElse(null);
        
        assertThat(failedTransaction).isNotNull();
        assertThat(failedTransaction.getStatus()).isEqualTo(TransactionStatus.FAILED);
        assertThat(failedTransaction.getFailureReason()).isNotNull();
        assertThat(failedTransaction.getFailureReason()).contains("Failed to create ledger entries");

        // Verify FAILED event was published
        verify(transactionEventProducer, times(1)).publish(argThat(e -> "TRANSACTION_FAILED".equals(e.getEventType())));
    }

    // ========== Withdrawal Tests ==========

    @Test
    @DisplayName("createWithdrawal should create transaction and ledger entries successfully")
    void createWithdrawalShouldCreateTransactionAndEntries() {
        // Given
        WithdrawalRequest request = WithdrawalRequest.builder()
                .fromWalletId(21L)
                .amount(new BigDecimal("50.00"))
                .currency("KES")
                .idempotencyKey("wd-1")
                .build();

        Transaction withdrawalTx = Transaction.builder()
                .id(2L)
                .transactionType(TransactionType.WITHDRAWAL)
                .amount(new BigDecimal("50.00"))
                .currency("KES")
                .fromWalletId(21L)
                .status(TransactionStatus.PENDING)
                .idempotencyKey("wd-1")
                .initiatedAt(LocalDateTime.now())
                .build();

        when(transactionRepository.findByIdempotencyKey("wd-1")).thenReturn(Optional.empty());
        when(transactionRepository.save(any(Transaction.class))).thenReturn(withdrawalTx);
        when(ledgerEntryRepository.save(any(LedgerEntry.class))).thenAnswer(invocation -> invocation.getArgument(0));

        // When
        TransactionResponse response = transactionService.createWithdrawal(request);

        // Then
        assertThat(response).isNotNull();
        assertThat(response.getId()).isEqualTo(2L);
        assertThat(response.getStatus()).isEqualTo("COMPLETED");

        verify(transactionRepository, times(2)).save(any(Transaction.class));
        verify(ledgerEntryRepository, times(2)).save(any(LedgerEntry.class));
    }

    @Test
    @DisplayName("createWithdrawal should throw exception when fromWalletId is null")
    void createWithdrawalShouldThrowWhenFromWalletIdIsNull() {
        // Given
        WithdrawalRequest request = WithdrawalRequest.builder()
                .fromWalletId(null)
                .amount(new BigDecimal("50.00"))
                .currency("KES")
                .idempotencyKey("wd-null")
                .build();

        // When/Then - Validation happens before repository call, so no stubbing needed
        assertThatThrownBy(() -> transactionService.createWithdrawal(request))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("fromWalletId is required for withdrawal");

        verify(transactionRepository, never()).findByIdempotencyKey(anyString());
        verify(transactionRepository, never()).save(any(Transaction.class));
    }

    // ========== Transfer Tests ==========

    @Test
    @DisplayName("createTransfer should create transaction and ledger entries successfully")
    void createTransferShouldCreateTransactionAndEntries() {
        // Given
        TransferRequest request = TransferRequest.builder()
                .fromWalletId(30L)
                .toWalletId(31L)
                .amount(new BigDecimal("25.00"))
                .currency("KES")
                .idempotencyKey("tr-1")
                .build();

        Transaction transferTx = Transaction.builder()
                .id(3L)
                .transactionType(TransactionType.TRANSFER)
                .amount(new BigDecimal("25.00"))
                .currency("KES")
                .fromWalletId(30L)
                .toWalletId(31L)
                .status(TransactionStatus.PENDING)
                .idempotencyKey("tr-1")
                .initiatedAt(LocalDateTime.now())
                .build();

        when(transactionRepository.findByIdempotencyKey("tr-1")).thenReturn(Optional.empty());
        when(transactionRepository.save(any(Transaction.class))).thenReturn(transferTx);
        when(ledgerEntryRepository.save(any(LedgerEntry.class))).thenAnswer(invocation -> invocation.getArgument(0));

        // When
        TransactionResponse response = transactionService.createTransfer(request);

        // Then
        assertThat(response).isNotNull();
        assertThat(response.getId()).isEqualTo(3L);
        assertThat(response.getStatus()).isEqualTo("COMPLETED");

        verify(transactionRepository, times(2)).save(any(Transaction.class));
        verify(ledgerEntryRepository, times(2)).save(any(LedgerEntry.class));
    }

    @Test
    @DisplayName("createTransfer should throw exception when fromWalletId is null")
    void createTransferShouldThrowWhenFromWalletIdIsNull() {
        // Given
        TransferRequest request = TransferRequest.builder()
                .fromWalletId(null)
                .toWalletId(31L)
                .amount(new BigDecimal("25.00"))
                .currency("KES")
                .idempotencyKey("tr-null-from")
                .build();

        // When/Then - Validation happens before repository call, so no stubbing needed
        assertThatThrownBy(() -> transactionService.createTransfer(request))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("fromWalletId is required for transfer");

        verify(transactionRepository, never()).findByIdempotencyKey(anyString());
        verify(transactionRepository, never()).save(any(Transaction.class));
    }

    @Test
    @DisplayName("createTransfer should throw exception when toWalletId is null")
    void createTransferShouldThrowWhenToWalletIdIsNull() {
        // Given
        TransferRequest request = TransferRequest.builder()
                .fromWalletId(30L)
                .toWalletId(null)
                .amount(new BigDecimal("25.00"))
                .currency("KES")
                .idempotencyKey("tr-null-to")
                .build();

        // When/Then - Validation happens before repository call, so no stubbing needed
        assertThatThrownBy(() -> transactionService.createTransfer(request))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("toWalletId is required for transfer");

        verify(transactionRepository, never()).findByIdempotencyKey(anyString());
        verify(transactionRepository, never()).save(any(Transaction.class));
    }

    @Test
    @DisplayName("createTransfer should throw exception when fromWalletId equals toWalletId")
    void createTransferShouldThrowWhenWalletsAreSame() {
        // Given
        TransferRequest request = TransferRequest.builder()
                .fromWalletId(30L)
                .toWalletId(30L)
                .amount(new BigDecimal("25.00"))
                .currency("KES")
                .idempotencyKey("tr-same")
                .build();

        // When/Then - Validation happens before repository call, so no stubbing needed
        assertThatThrownBy(() -> transactionService.createTransfer(request))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("fromWalletId and toWalletId must differ");

        verify(transactionRepository, never()).findByIdempotencyKey(anyString());
        verify(transactionRepository, never()).save(any(Transaction.class));
    }

    // ========== Amount Validation Tests ==========

    @Test
    @DisplayName("createDeposit should throw exception when amount is null")
    void createDepositShouldThrowWhenAmountIsNull() {
        // Given
        DepositRequest request = DepositRequest.builder()
                .toWalletId(20L)
                .amount(null)
                .currency("KES")
                .idempotencyKey("dep-null-amount")
                .build();

        // When/Then
        assertThatThrownBy(() -> transactionService.createDeposit(request))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Amount must be greater than 0");

        verify(transactionRepository, never()).save(any(Transaction.class));
    }

    @Test
    @DisplayName("createDeposit should throw exception when amount is zero")
    void createDepositShouldThrowWhenAmountIsZero() {
        // Given
        DepositRequest request = DepositRequest.builder()
                .toWalletId(20L)
                .amount(BigDecimal.ZERO)
                .currency("KES")
                .idempotencyKey("dep-zero")
                .build();

        // When/Then
        assertThatThrownBy(() -> transactionService.createDeposit(request))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Amount must be greater than 0");

        verify(transactionRepository, never()).save(any(Transaction.class));
    }

    @Test
    @DisplayName("createDeposit should throw exception when amount is negative")
    void createDepositShouldThrowWhenAmountIsNegative() {
        // Given
        DepositRequest request = DepositRequest.builder()
                .toWalletId(20L)
                .amount(new BigDecimal("-10.00"))
                .currency("KES")
                .idempotencyKey("dep-negative")
                .build();

        // When/Then
        assertThatThrownBy(() -> transactionService.createDeposit(request))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Amount must be greater than 0");

        verify(transactionRepository, never()).save(any(Transaction.class));
    }

    // ========== Currency Validation Tests ==========

    @Test
    @DisplayName("createDeposit should normalize currency to uppercase")
    void createDepositShouldNormalizeCurrency() {
        // Given
        DepositRequest request = DepositRequest.builder()
                .toWalletId(20L)
                .amount(new BigDecimal("100.00"))
                .currency("kes")
                .idempotencyKey("dep-currency")
                .build();

        when(transactionRepository.findByIdempotencyKey("dep-currency")).thenReturn(Optional.empty());
        when(transactionRepository.save(any(Transaction.class))).thenAnswer(invocation -> {
            Transaction tx = invocation.getArgument(0);
            tx.setId(1L);
            return tx;
        });
        when(ledgerEntryRepository.save(any(LedgerEntry.class))).thenAnswer(invocation -> invocation.getArgument(0));

        // When
        TransactionResponse response = transactionService.createDeposit(request);

        // Then
        assertThat(response.getCurrency()).isEqualTo("KES");
    }

    @Test
    @DisplayName("createDeposit should default to KES when currency is null")
    void createDepositShouldDefaultToKESWhenCurrencyIsNull() {
        // Given
        DepositRequest request = DepositRequest.builder()
                .toWalletId(20L)
                .amount(new BigDecimal("100.00"))
                .currency(null)
                .idempotencyKey("dep-null-currency")
                .build();

        when(transactionRepository.findByIdempotencyKey("dep-null-currency")).thenReturn(Optional.empty());
        when(transactionRepository.save(any(Transaction.class))).thenAnswer(invocation -> {
            Transaction tx = invocation.getArgument(0);
            tx.setId(1L);
            return tx;
        });
        when(ledgerEntryRepository.save(any(LedgerEntry.class))).thenAnswer(invocation -> invocation.getArgument(0));

        // When
        TransactionResponse response = transactionService.createDeposit(request);

        // Then
        assertThat(response.getCurrency()).isEqualTo("KES");
    }

    @Test
    @DisplayName("createDeposit should reject non-KES currency")
    void createDepositShouldRejectNonKESCurrency() {
        // Given
        DepositRequest request = DepositRequest.builder()
                .toWalletId(20L)
                .amount(new BigDecimal("100.00"))
                .currency("USD")
                .idempotencyKey("dep-usd")
                .build();

        // When/Then
        assertThatThrownBy(() -> transactionService.createDeposit(request))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Only KES currency is supported");
    }

    @Test
    @DisplayName("createWithdrawal should reject non-KES currency")
    void createWithdrawalShouldRejectNonKESCurrency() {
        // Given
        WithdrawalRequest request = WithdrawalRequest.builder()
                .fromWalletId(21L)
                .amount(new BigDecimal("50.00"))
                .currency("EUR")
                .idempotencyKey("wd-eur")
                .build();

        // When/Then
        assertThatThrownBy(() -> transactionService.createWithdrawal(request))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Only KES currency is supported");
    }

    @Test
    @DisplayName("createTransfer should reject non-KES currency")
    void createTransferShouldRejectNonKESCurrency() {
        // Given
        TransferRequest request = TransferRequest.builder()
                .fromWalletId(30L)
                .toWalletId(31L)
                .amount(new BigDecimal("25.00"))
                .currency("GBP")
                .idempotencyKey("tr-gbp")
                .build();

        // When/Then
        assertThatThrownBy(() -> transactionService.createTransfer(request))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Only KES currency is supported");
    }

    // ========== Get Transaction Tests ==========

    @Test
    @DisplayName("getTransaction should return transaction when found")
    void getTransactionShouldReturnTransactionWhenFound() {
        // Given
        Transaction tx = Transaction.builder()
                .id(1L)
                .transactionType(TransactionType.DEPOSIT)
                .status(TransactionStatus.COMPLETED)
                .amount(new BigDecimal("100.00"))
                .currency("KES")
                .build();

        when(transactionRepository.findById(1L)).thenReturn(Optional.of(tx));

        // When
        TransactionResponse response = transactionService.getTransaction(1L);

        // Then
        assertThat(response).isNotNull();
        assertThat(response.getId()).isEqualTo(1L);
        verify(transactionRepository, times(1)).findById(1L);
    }

    @Test
    @DisplayName("getTransaction should throw exception when not found")
    void getTransactionShouldThrowWhenNotFound() {
        // Given
        when(transactionRepository.findById(999L)).thenReturn(Optional.empty());

        // When/Then
        assertThatThrownBy(() -> transactionService.getTransaction(999L))
                .isInstanceOf(TransactionNotFoundException.class)
                .hasMessageContaining("Transaction not found");
    }

    // ========== Event Publishing Error Handling Tests ==========

    @Test
    @DisplayName("createDeposit should continue when event publishing fails")
    void createDepositShouldContinueWhenEventPublishingFails() {
        // Given
        DepositRequest request = DepositRequest.builder()
                .toWalletId(20L)
                .amount(new BigDecimal("100.00"))
                .currency("KES")
                .idempotencyKey("dep-event-fail")
                .build();

        when(transactionRepository.findByIdempotencyKey("dep-event-fail")).thenReturn(Optional.empty());
        when(transactionRepository.save(any(Transaction.class))).thenReturn(savedTransaction);
        when(ledgerEntryRepository.save(any(LedgerEntry.class))).thenAnswer(invocation -> invocation.getArgument(0));
        doThrow(new RuntimeException("Kafka error")).when(transactionEventProducer).publish(any(TransactionEvent.class));

        // When
        TransactionResponse response = transactionService.createDeposit(request);

        // Then - Transaction should still complete despite event publishing failure
        assertThat(response).isNotNull();
        assertThat(response.getStatus()).isEqualTo("COMPLETED");
        verify(transactionRepository, times(2)).save(any(Transaction.class));
        verify(ledgerEntryRepository, times(2)).save(any(LedgerEntry.class));
    }

    // ========== Double Entry Creation Tests ==========

    @Test
    @DisplayName("createDeposit should create correct ledger entries")
    void createDepositShouldCreateCorrectLedgerEntries() {
        // Given
        DepositRequest request = DepositRequest.builder()
                .toWalletId(20L)
                .amount(new BigDecimal("100.00"))
                .currency("KES")
                .idempotencyKey("dep-entries")
                .build();

        when(transactionRepository.findByIdempotencyKey("dep-entries")).thenReturn(Optional.empty());
        when(transactionRepository.save(any(Transaction.class))).thenReturn(savedTransaction);
        when(ledgerEntryRepository.save(any(LedgerEntry.class))).thenAnswer(invocation -> invocation.getArgument(0));

        // When
        transactionService.createDeposit(request);

        // Then - Verify ledger entries
        ArgumentCaptor<LedgerEntry> entryCaptor = ArgumentCaptor.forClass(LedgerEntry.class);
        verify(ledgerEntryRepository, times(2)).save(entryCaptor.capture());

        var entries = entryCaptor.getAllValues();
        
        // First entry should be DEBIT to CASH_ACCOUNT
        LedgerEntry debitEntry = entries.stream()
                .filter(e -> e.getEntryType() == EntryType.DEBIT && "CASH_ACCOUNT".equals(e.getAccountType()))
                .findFirst()
                .orElse(null);
        assertThat(debitEntry).isNotNull();
        assertThat(debitEntry.getAmount()).isEqualByComparingTo(new BigDecimal("100.00"));

        // Second entry should be CREDIT to WALLET
        LedgerEntry creditEntry = entries.stream()
                .filter(e -> e.getEntryType() == EntryType.CREDIT && e.getAccountType().startsWith("WALLET_"))
                .findFirst()
                .orElse(null);
        assertThat(creditEntry).isNotNull();
        assertThat(creditEntry.getWalletId()).isEqualTo(20L);
        assertThat(creditEntry.getAmount()).isEqualByComparingTo(new BigDecimal("100.00"));
    }
}

