package com.openwallet.ledger.service;

import com.openwallet.ledger.config.JpaConfig;
import com.openwallet.ledger.domain.LedgerEntry;
import com.openwallet.ledger.domain.Transaction;
import com.openwallet.ledger.domain.TransactionStatus;
import com.openwallet.ledger.dto.DepositRequest;
import com.openwallet.ledger.dto.TransferRequest;
import com.openwallet.ledger.events.TransactionEventProducer;
import com.openwallet.ledger.repository.LedgerEntryRepository;
import com.openwallet.ledger.repository.TransactionRepository;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.annotation.Rollback;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.jdbc.Sql;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

@SpringBootTest
@Import({JpaConfig.class, com.openwallet.ledger.config.TestSecurityConfig.class})
@ActiveProfiles("test")
@Transactional
@Rollback
@Sql(scripts = "/sql/create_wallets_table.sql", executionPhase = Sql.ExecutionPhase.BEFORE_TEST_METHOD)
class TransactionServiceIntegrationTest {

    @Autowired
    private TransactionService transactionService;

    @Autowired
    private TransactionRepository transactionRepository;

    @Autowired
    private LedgerEntryRepository ledgerEntryRepository;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @MockBean
    private TransactionEventProducer transactionEventProducer;

    private void createTestWallet(Long walletId, BigDecimal dailyLimit, BigDecimal monthlyLimit) {
        jdbcTemplate.update(
            "INSERT INTO wallets (id, customer_id, currency, balance, daily_limit, monthly_limit, status) " +
            "VALUES (?, 1, 'KES', 0.00, ?, ?, 'ACTIVE')",
            walletId, dailyLimit, monthlyLimit
        );
    }

    @Test
    void depositCreatesDoubleEntryAndCompletes() {
        // Setup: Create test wallet with high limits
        createTestWallet(200L, new BigDecimal("100000.00"), new BigDecimal("1000000.00"));

        DepositRequest request = DepositRequest.builder()
                .toWalletId(200L)
                .amount(new BigDecimal("50.00"))
                .currency("KES")
                .idempotencyKey("it-dep-1")
                .build();

        transactionService.createDeposit(request);

        List<Transaction> txs = transactionRepository.findAll();
        assertThat(txs).hasSize(1);
        Transaction tx = txs.get(0);
        assertThat(tx.getStatus()).isEqualTo(TransactionStatus.COMPLETED);

        List<LedgerEntry> entries = ledgerEntryRepository.findByTransactionId(tx.getId());
        assertThat(entries).hasSize(2);
        assertThat(entries).extracting(LedgerEntry::getEntryType).containsExactlyInAnyOrder(
                com.openwallet.ledger.domain.EntryType.DEBIT,
                com.openwallet.ledger.domain.EntryType.CREDIT);
        BigDecimal debitSum = entries.stream()
                .filter(e -> e.getEntryType() == com.openwallet.ledger.domain.EntryType.DEBIT)
                .map(LedgerEntry::getAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal creditSum = entries.stream()
                .filter(e -> e.getEntryType() == com.openwallet.ledger.domain.EntryType.CREDIT)
                .map(LedgerEntry::getAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        assertThat(debitSum).isEqualByComparingTo(creditSum);
        assertThat(entries.stream().map(LedgerEntry::getAmount).collect(Collectors.toSet()))
                .containsExactly(request.getAmount());

        verify(transactionEventProducer, times(1))
                .publish(Mockito.argThat(e -> "TRANSACTION_INITIATED".equals(e.getEventType())));
        verify(transactionEventProducer, times(1))
                .publish(Mockito.argThat(e -> "TRANSACTION_COMPLETED".equals(e.getEventType())));
    }

    @Test
    void transferCreatesDoubleEntryAndCompletes() {
        // Setup: Create test wallets with high limits
        createTestWallet(201L, new BigDecimal("100000.00"), new BigDecimal("1000000.00"));
        createTestWallet(202L, new BigDecimal("100000.00"), new BigDecimal("1000000.00"));

        TransferRequest request = TransferRequest.builder()
                .fromWalletId(201L)
                .toWalletId(202L)
                .amount(new BigDecimal("75.00"))
                .currency("KES")
                .idempotencyKey("it-tr-1")
                .build();

        transactionService.createTransfer(request);

        List<Transaction> txs = transactionRepository.findAll();
        assertThat(txs).hasSize(1);
        Transaction tx = txs.get(0);
        assertThat(tx.getStatus()).isEqualTo(TransactionStatus.COMPLETED);

        List<LedgerEntry> entries = ledgerEntryRepository.findByTransactionId(tx.getId());
        assertThat(entries).hasSize(2);
        assertThat(entries).extracting(LedgerEntry::getEntryType).containsExactlyInAnyOrder(
                com.openwallet.ledger.domain.EntryType.DEBIT,
                com.openwallet.ledger.domain.EntryType.CREDIT);
        BigDecimal debitSum = entries.stream()
                .filter(e -> e.getEntryType() == com.openwallet.ledger.domain.EntryType.DEBIT)
                .map(LedgerEntry::getAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal creditSum = entries.stream()
                .filter(e -> e.getEntryType() == com.openwallet.ledger.domain.EntryType.CREDIT)
                .map(LedgerEntry::getAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        assertThat(debitSum).isEqualByComparingTo(creditSum);
        assertThat(entries.stream().map(LedgerEntry::getAmount).collect(Collectors.toSet()))
                .containsExactly(request.getAmount());

        verify(transactionEventProducer, times(1))
                .publish(Mockito.argThat(e -> "TRANSACTION_INITIATED".equals(e.getEventType())));
        verify(transactionEventProducer, times(1))
                .publish(Mockito.argThat(e -> "TRANSACTION_COMPLETED".equals(e.getEventType())));
    }

    @Test
    void idempotencyReturnsExistingTransaction() {
        // Setup: Create test wallet with high limits
        createTestWallet(300L, new BigDecimal("100000.00"), new BigDecimal("1000000.00"));

        DepositRequest request = DepositRequest.builder()
                .toWalletId(300L)
                .amount(new BigDecimal("10.00"))
                .currency("KES")
                .idempotencyKey("it-dep-same")
                .build();

        transactionService.createDeposit(request);
        transactionService.createDeposit(request);

        List<Transaction> txs = transactionRepository.findAll();
        assertThat(txs).hasSize(1);
        verify(transactionEventProducer, times(1))
                .publish(Mockito.argThat(e -> "TRANSACTION_INITIATED".equals(e.getEventType())));
        verify(transactionEventProducer, times(1))
                .publish(Mockito.argThat(e -> "TRANSACTION_COMPLETED".equals(e.getEventType())));
    }
}
