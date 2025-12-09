package com.openwallet.ledger.service;

import com.openwallet.ledger.domain.LedgerEntry;
import com.openwallet.ledger.domain.Transaction;
import com.openwallet.ledger.domain.TransactionStatus;
import com.openwallet.ledger.dto.DepositRequest;
import com.openwallet.ledger.dto.TransferRequest;
import com.openwallet.ledger.dto.WithdrawalRequest;
import com.openwallet.ledger.dto.TransactionResponse;
import com.openwallet.ledger.exception.TransactionNotFoundException;
import com.openwallet.ledger.repository.LedgerEntryRepository;
import com.openwallet.ledger.repository.TransactionRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DataJpaTest
@Import({ com.openwallet.ledger.config.JpaConfig.class, TransactionService.class })
@ActiveProfiles("test")
@SuppressWarnings({ "ConstantConditions", "DataFlowIssue" })
class TransactionServiceTest {

    @Autowired
    private TransactionService transactionService;

    @Autowired
    private TransactionRepository transactionRepository;

    @Autowired
    private LedgerEntryRepository ledgerEntryRepository;

    @Test
    void createDepositShouldPersistTransactionAndEntries() {
        DepositRequest request = DepositRequest.builder()
                .toWalletId(20L)
                .amount(new BigDecimal("100.00"))
                .currency("kes")
                .idempotencyKey("dep-1")
                .build();

        TransactionResponse response = transactionService.createDeposit(request);

        assertThat(response.getId()).isNotNull();
        Transaction saved = transactionRepository.findById(response.getId())
                .orElseThrow(() -> new IllegalStateException("Transaction missing"));
        assertThat(saved.getStatus()).isEqualTo(TransactionStatus.COMPLETED);
        List<LedgerEntry> entries = ledgerEntryRepository.findByTransactionId(saved.getId());
        assertThat(entries).hasSize(2);
    }

    @Test
    void createWithdrawalShouldPersistTransactionAndEntries() {
        WithdrawalRequest request = WithdrawalRequest.builder()
                .fromWalletId(21L)
                .amount(new BigDecimal("50.00"))
                .currency("USD")
                .idempotencyKey("wd-1")
                .build();

        TransactionResponse response = transactionService.createWithdrawal(request);

        Transaction saved = transactionRepository.findById(response.getId())
                .orElseThrow(() -> new IllegalStateException("Transaction missing"));
        assertThat(saved.getStatus()).isEqualTo(TransactionStatus.COMPLETED);
        List<LedgerEntry> entries = ledgerEntryRepository.findByTransactionId(saved.getId());
        assertThat(entries).hasSize(2);
    }

    @Test
    void createTransferShouldPersistTransactionAndEntries() {
        TransferRequest request = TransferRequest.builder()
                .fromWalletId(30L)
                .toWalletId(31L)
                .amount(new BigDecimal("25.00"))
                .currency("EUR")
                .idempotencyKey("tr-1")
                .build();

        TransactionResponse response = transactionService.createTransfer(request);

        Transaction saved = transactionRepository.findById(response.getId())
                .orElseThrow(() -> new IllegalStateException("Transaction missing"));
        assertThat(saved.getStatus()).isEqualTo(TransactionStatus.COMPLETED);
        List<LedgerEntry> entries = ledgerEntryRepository.findByTransactionId(saved.getId());
        assertThat(entries).hasSize(2);
    }

    @Test
    void idempotencyShouldReturnExistingTransaction() {
        DepositRequest request = DepositRequest.builder()
                .toWalletId(50L)
                .amount(new BigDecimal("10.00"))
                .currency("KES")
                .idempotencyKey("dep-same")
                .build();

        TransactionResponse first = transactionService.createDeposit(request);
        TransactionResponse second = transactionService.createDeposit(request);

        assertThat(second.getId()).isEqualTo(first.getId());
        assertThat(transactionRepository.findAll()).hasSize(1);
    }

    @Test
    void getTransactionShouldThrowWhenMissing() {
        assertThatThrownBy(() -> transactionService.getTransaction(999L))
                .isInstanceOf(TransactionNotFoundException.class);
    }
}
