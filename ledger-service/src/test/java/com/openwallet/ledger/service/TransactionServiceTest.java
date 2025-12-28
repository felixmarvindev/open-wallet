package com.openwallet.ledger.service;

import com.openwallet.ledger.domain.LedgerEntry;
import com.openwallet.ledger.domain.Transaction;
import com.openwallet.ledger.domain.TransactionStatus;
import com.openwallet.ledger.dto.DepositRequest;
import com.openwallet.ledger.dto.TransferRequest;
import com.openwallet.ledger.dto.WithdrawalRequest;
import com.openwallet.ledger.dto.TransactionResponse;
import com.openwallet.ledger.exception.TransactionNotFoundException;
import com.openwallet.ledger.events.TransactionEventProducer;
import com.openwallet.ledger.repository.LedgerEntryRepository;
import com.openwallet.ledger.repository.TransactionRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.autoconfigure.orm.jpa.TestEntityManager;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.jdbc.Sql;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DataJpaTest
@Import({ com.openwallet.ledger.config.JpaConfig.class, TransactionService.class, LedgerEntryService.class, 
         WalletLimitsService.class, TransactionLimitService.class })
@ActiveProfiles("test")
@Sql(scripts = "/sql/create_wallets_table.sql", executionPhase = Sql.ExecutionPhase.BEFORE_TEST_METHOD)
@SuppressWarnings({ "ConstantConditions", "DataFlowIssue", "null" })
class TransactionServiceTest {

    @Autowired
    private TransactionService transactionService;

    @Autowired
    private TransactionRepository transactionRepository;

    @Autowired
    private LedgerEntryRepository ledgerEntryRepository;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private TestEntityManager testEntityManager;

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
    void createDepositShouldPersistTransactionAndEntries() {
        // Setup: Create test wallet with high limits
        createTestWallet(20L, new BigDecimal("100000.00"), new BigDecimal("1000000.00"));

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
        // Setup: Create test wallet with high limits
        createTestWallet(21L, new BigDecimal("100000.00"), new BigDecimal("1000000.00"));

        WithdrawalRequest request = WithdrawalRequest.builder()
                .fromWalletId(21L)
                .amount(new BigDecimal("50.00"))
                .currency("KES")
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
        // Setup: Create test wallets with high limits
        createTestWallet(30L, new BigDecimal("100000.00"), new BigDecimal("1000000.00"));
        createTestWallet(31L, new BigDecimal("100000.00"), new BigDecimal("1000000.00"));

        TransferRequest request = TransferRequest.builder()
                .fromWalletId(30L)
                .toWalletId(31L)
                .amount(new BigDecimal("25.00"))
                .currency("KES")
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
    void createDepositShouldRejectNonKESCurrency() {
        DepositRequest request = DepositRequest.builder()
                .toWalletId(20L)
                .amount(new BigDecimal("100.00"))
                .currency("USD")
                .idempotencyKey("dep-usd")
                .build();

        assertThatThrownBy(() -> transactionService.createDeposit(request))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Only KES currency is supported");
    }

    @Test
    void createWithdrawalShouldRejectNonKESCurrency() {
        WithdrawalRequest request = WithdrawalRequest.builder()
                .fromWalletId(21L)
                .amount(new BigDecimal("50.00"))
                .currency("EUR")
                .idempotencyKey("wd-eur")
                .build();

        assertThatThrownBy(() -> transactionService.createWithdrawal(request))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Only KES currency is supported");
    }

    @Test
    void createTransferShouldRejectNonKESCurrency() {
        TransferRequest request = TransferRequest.builder()
                .fromWalletId(30L)
                .toWalletId(31L)
                .amount(new BigDecimal("25.00"))
                .currency("GBP")
                .idempotencyKey("tr-gbp")
                .build();

        assertThatThrownBy(() -> transactionService.createTransfer(request))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Only KES currency is supported");
    }

    @Test
    void idempotencyShouldReturnExistingTransaction() {
        // Setup: Create test wallet with high limits
        createTestWallet(50L, new BigDecimal("100000.00"), new BigDecimal("1000000.00"));

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

    @Test
    void createDepositShouldThrowWhenToWalletIdIsNull() {
        DepositRequest request = DepositRequest.builder()
                .toWalletId(null)
                .amount(new BigDecimal("100.00"))
                .currency("KES")
                .idempotencyKey("dep-null-wallet")
                .build();

        assertThatThrownBy(() -> transactionService.createDeposit(request))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("toWalletId is required for deposit");
    }

    @Test
    void createWithdrawalShouldThrowWhenFromWalletIdIsNull() {
        WithdrawalRequest request = WithdrawalRequest.builder()
                .fromWalletId(null)
                .amount(new BigDecimal("50.00"))
                .currency("KES")
                .idempotencyKey("wd-null-wallet")
                .build();

        assertThatThrownBy(() -> transactionService.createWithdrawal(request))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("fromWalletId is required for withdrawal");
    }

    @Test
    void createTransferShouldThrowWhenFromWalletIdIsNull() {
        TransferRequest request = TransferRequest.builder()
                .fromWalletId(null)
                .toWalletId(31L)
                .amount(new BigDecimal("25.00"))
                .currency("KES")
                .idempotencyKey("tr-null-from")
                .build();

        assertThatThrownBy(() -> transactionService.createTransfer(request))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("fromWalletId is required for transfer");
    }

    @Test
    void createTransferShouldThrowWhenToWalletIdIsNull() {
        TransferRequest request = TransferRequest.builder()
                .fromWalletId(30L)
                .toWalletId(null)
                .amount(new BigDecimal("25.00"))
                .currency("KES")
                .idempotencyKey("tr-null-to")
                .build();

        assertThatThrownBy(() -> transactionService.createTransfer(request))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("toWalletId is required for transfer");
    }

    @Test
    void createTransferShouldThrowWhenWalletsAreSame() {
        TransferRequest request = TransferRequest.builder()
                .fromWalletId(30L)
                .toWalletId(30L)
                .amount(new BigDecimal("25.00"))
                .currency("KES")
                .idempotencyKey("tr-same-wallet")
                .build();

        assertThatThrownBy(() -> transactionService.createTransfer(request))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("fromWalletId and toWalletId must differ");
    }

    @Test
    void createDepositShouldThrowWhenAmountIsInvalid() {
        DepositRequest request = DepositRequest.builder()
                .toWalletId(20L)
                .amount(BigDecimal.ZERO)
                .currency("KES")
                .idempotencyKey("dep-zero-amount")
                .build();

        assertThatThrownBy(() -> transactionService.createDeposit(request))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Amount must be greater than 0");
    }

    @Test
    void createDepositShouldThrowWhenDailyLimitExceeded() {
        // Setup: Create test wallet with low daily limit
        createTestWallet(100L, new BigDecimal("100.00"), new BigDecimal("10000.00"));

        // First transaction uses up most of the limit
        DepositRequest firstRequest = DepositRequest.builder()
                .toWalletId(100L)
                .amount(new BigDecimal("90.00"))
                .currency("KES")
                .idempotencyKey("dep-limit-1")
                .build();
        transactionService.createDeposit(firstRequest);
        
        // Flush to ensure first transaction is visible to limit query
        testEntityManager.flush();
        testEntityManager.clear();

        // Second transaction should exceed daily limit (90 + 20 = 110 > 100)
        DepositRequest secondRequest = DepositRequest.builder()
                .toWalletId(100L)
                .amount(new BigDecimal("20.00"))
                .currency("KES")
                .idempotencyKey("dep-limit-2")
                .build();

        assertThatThrownBy(() -> transactionService.createDeposit(secondRequest))
                .isInstanceOf(com.openwallet.ledger.exception.LimitExceededException.class)
                .hasMessageContaining("DAILY limit");
    }

    @Test
    void createDepositShouldThrowWhenMonthlyLimitExceeded() {
        // Setup: Create test wallet with low monthly limit
        createTestWallet(101L, new BigDecimal("10000.00"), new BigDecimal("1000.00"));

        // First transaction uses up most of the monthly limit
        DepositRequest firstRequest = DepositRequest.builder()
                .toWalletId(101L)
                .amount(new BigDecimal("950.00"))
                .currency("KES")
                .idempotencyKey("dep-monthly-1")
                .build();
        transactionService.createDeposit(firstRequest);
        
        // Flush to ensure first transaction is visible to limit query
        testEntityManager.flush();
        testEntityManager.clear();

        // Second transaction should exceed monthly limit (950 + 100 = 1050 > 1000)
        DepositRequest secondRequest = DepositRequest.builder()
                .toWalletId(101L)
                .amount(new BigDecimal("100.00"))
                .currency("KES")
                .idempotencyKey("dep-monthly-2")
                .build();

        assertThatThrownBy(() -> transactionService.createDeposit(secondRequest))
                .isInstanceOf(com.openwallet.ledger.exception.LimitExceededException.class)
                .hasMessageContaining("MONTHLY limit");
    }

    @Test
    void createDepositShouldThrowWhenWalletNotFound() {
        DepositRequest request = DepositRequest.builder()
                .toWalletId(999L)
                .amount(new BigDecimal("100.00"))
                .currency("KES")
                .idempotencyKey("dep-missing-wallet")
                .build();

        assertThatThrownBy(() -> transactionService.createDeposit(request))
                .isInstanceOf(com.openwallet.ledger.exception.WalletNotFoundException.class)
                .hasMessageContaining("Wallet not found");
    }
}
