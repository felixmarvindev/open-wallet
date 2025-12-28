package com.openwallet.ledger.service;

import com.openwallet.ledger.domain.LedgerEntry;
import com.openwallet.ledger.domain.Transaction;
import com.openwallet.ledger.domain.TransactionStatus;
import com.openwallet.ledger.domain.TransactionType;
import com.openwallet.ledger.dto.DepositRequest;
import com.openwallet.ledger.dto.TransferRequest;
import com.openwallet.ledger.dto.TransactionListResponse;
import com.openwallet.ledger.dto.TransactionResponse;
import com.openwallet.ledger.dto.WithdrawalRequest;
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
import java.time.LocalDateTime;
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

    // ========== Transaction History Query Tests ==========

    @Test
    void getTransactionsShouldReturnAllTransactionsWhenNoFilters() {
        // Setup: Create test wallets and transactions
        createTestWallet(200L, new BigDecimal("100000.00"), new BigDecimal("1000000.00"));
        createTestWallet(201L, new BigDecimal("100000.00"), new BigDecimal("1000000.00"));

        DepositRequest dep1 = DepositRequest.builder()
                .toWalletId(200L)
                .amount(new BigDecimal("100.00"))
                .currency("KES")
                .idempotencyKey("hist-dep-1")
                .build();
        transactionService.createDeposit(dep1);

        WithdrawalRequest wd1 = WithdrawalRequest.builder()
                .fromWalletId(200L)
                .amount(new BigDecimal("50.00"))
                .currency("KES")
                .idempotencyKey("hist-wd-1")
                .build();
        transactionService.createWithdrawal(wd1);

        TransferRequest tr1 = TransferRequest.builder()
                .fromWalletId(200L)
                .toWalletId(201L)
                .amount(new BigDecimal("25.00"))
                .currency("KES")
                .idempotencyKey("hist-tr-1")
                .build();
        transactionService.createTransfer(tr1);

        testEntityManager.flush();
        testEntityManager.clear();

        // When: Query all transactions
        TransactionListResponse response = transactionService.getTransactions(
                null, null, null, null, null, 0, 20, null, null
        );

        // Then: Should return all transactions
        assertThat(response.getTransactions()).isNotEmpty();
        assertThat(response.getPagination().getTotalElements()).isGreaterThanOrEqualTo(3);
        assertThat(response.getPagination().getPage()).isEqualTo(0);
        assertThat(response.getPagination().getSize()).isEqualTo(20);
    }

    @Test
    void getTransactionsShouldFilterByWalletId() {
        // Setup: Create test wallets and transactions
        createTestWallet(300L, new BigDecimal("100000.00"), new BigDecimal("1000000.00"));
        createTestWallet(301L, new BigDecimal("100000.00"), new BigDecimal("1000000.00"));

        DepositRequest dep1 = DepositRequest.builder()
                .toWalletId(300L)
                .amount(new BigDecimal("100.00"))
                .currency("KES")
                .idempotencyKey("wallet-filter-dep-1")
                .build();
        transactionService.createDeposit(dep1);

        DepositRequest dep2 = DepositRequest.builder()
                .toWalletId(301L)
                .amount(new BigDecimal("200.00"))
                .currency("KES")
                .idempotencyKey("wallet-filter-dep-2")
                .build();
        transactionService.createDeposit(dep2);

        testEntityManager.flush();
        testEntityManager.clear();

        // When: Query transactions for wallet 300
        TransactionListResponse response = transactionService.getTransactions(
                300L, null, null, null, null, 0, 20, null, null
        );

        // Then: Should return only transactions involving wallet 300
        assertThat(response.getTransactions()).isNotEmpty();
        assertThat(response.getTransactions()).allMatch(tx -> 
                tx.getToWalletId() != null && tx.getToWalletId().equals(300L) ||
                tx.getFromWalletId() != null && tx.getFromWalletId().equals(300L)
        );
    }

    @Test
    void getTransactionsShouldFilterByDateRange() {
        // Setup: Create test wallet
        createTestWallet(400L, new BigDecimal("100000.00"), new BigDecimal("1000000.00"));

        LocalDateTime now = LocalDateTime.now();
        LocalDateTime yesterday = now.minusDays(1);
        LocalDateTime tomorrow = now.plusDays(1);

        DepositRequest dep1 = DepositRequest.builder()
                .toWalletId(400L)
                .amount(new BigDecimal("100.00"))
                .currency("KES")
                .idempotencyKey("date-filter-dep-1")
                .build();
        transactionService.createDeposit(dep1);

        testEntityManager.flush();
        testEntityManager.clear();

        // When: Query transactions within date range
        TransactionListResponse response = transactionService.getTransactions(
                null, yesterday, tomorrow, null, null, 0, 20, null, null
        );

        // Then: Should return transactions in the date range
        assertThat(response.getTransactions()).isNotEmpty();
    }

    @Test
    void getTransactionsShouldFilterByStatus() {
        // Setup: Create test wallet
        createTestWallet(500L, new BigDecimal("100000.00"), new BigDecimal("1000000.00"));

        DepositRequest dep1 = DepositRequest.builder()
                .toWalletId(500L)
                .amount(new BigDecimal("100.00"))
                .currency("KES")
                .idempotencyKey("status-filter-dep-1")
                .build();
        transactionService.createDeposit(dep1);

        testEntityManager.flush();
        testEntityManager.clear();

        // When: Query only COMPLETED transactions
        TransactionListResponse response = transactionService.getTransactions(
                null, null, null, TransactionStatus.COMPLETED, null, 0, 20, null, null
        );

        // Then: Should return only COMPLETED transactions
        assertThat(response.getTransactions()).isNotEmpty();
        assertThat(response.getTransactions()).allMatch(tx -> 
                "COMPLETED".equals(tx.getStatus())
        );
    }

    @Test
    void getTransactionsShouldFilterByTransactionType() {
        // Setup: Create test wallets
        createTestWallet(600L, new BigDecimal("100000.00"), new BigDecimal("1000000.00"));
        createTestWallet(601L, new BigDecimal("100000.00"), new BigDecimal("1000000.00"));

        DepositRequest dep1 = DepositRequest.builder()
                .toWalletId(600L)
                .amount(new BigDecimal("100.00"))
                .currency("KES")
                .idempotencyKey("type-filter-dep-1")
                .build();
        transactionService.createDeposit(dep1);

        WithdrawalRequest wd1 = WithdrawalRequest.builder()
                .fromWalletId(600L)
                .amount(new BigDecimal("50.00"))
                .currency("KES")
                .idempotencyKey("type-filter-wd-1")
                .build();
        transactionService.createWithdrawal(wd1);

        testEntityManager.flush();
        testEntityManager.clear();

        // When: Query only DEPOSIT transactions
        TransactionListResponse response = transactionService.getTransactions(
                null, null, null, null, TransactionType.DEPOSIT, 0, 20, null, null
        );

        // Then: Should return only DEPOSIT transactions
        assertThat(response.getTransactions()).isNotEmpty();
        assertThat(response.getTransactions()).allMatch(tx -> 
                "DEPOSIT".equals(tx.getTransactionType())
        );
    }

    @Test
    void getTransactionsShouldSupportPagination() {
        // Setup: Create test wallet and multiple transactions
        createTestWallet(700L, new BigDecimal("100000.00"), new BigDecimal("1000000.00"));

        for (int i = 1; i <= 5; i++) {
            DepositRequest request = DepositRequest.builder()
                    .toWalletId(700L)
                    .amount(new BigDecimal("10.00"))
                    .currency("KES")
                    .idempotencyKey("pagination-dep-" + i)
                    .build();
            transactionService.createDeposit(request);
        }

        testEntityManager.flush();
        testEntityManager.clear();

        // When: Query first page with size 2
        TransactionListResponse page1 = transactionService.getTransactions(
                700L, null, null, null, null, 0, 2, null, null
        );

        // Then: Should return 2 transactions
        assertThat(page1.getTransactions()).hasSize(2);
        assertThat(page1.getPagination().getPage()).isEqualTo(0);
        assertThat(page1.getPagination().getSize()).isEqualTo(2);
        assertThat(page1.getPagination().getTotalElements()).isGreaterThanOrEqualTo(5);
        assertThat(page1.getPagination().isHasNext()).isTrue();

        // When: Query second page
        TransactionListResponse page2 = transactionService.getTransactions(
                700L, null, null, null, null, 1, 2, null, null
        );

        // Then: Should return next 2 transactions
        assertThat(page2.getTransactions()).hasSize(2);
        assertThat(page2.getPagination().getPage()).isEqualTo(1);
        assertThat(page2.getPagination().isHasPrevious()).isTrue();
    }

    @Test
    void getTransactionsShouldSupportSorting() {
        // Setup: Create test wallet and transactions
        createTestWallet(800L, new BigDecimal("100000.00"), new BigDecimal("1000000.00"));

        DepositRequest dep1 = DepositRequest.builder()
                .toWalletId(800L)
                .amount(new BigDecimal("100.00"))
                .currency("KES")
                .idempotencyKey("sort-dep-1")
                .build();
        transactionService.createDeposit(dep1);

        // Small delay to ensure different timestamps
        try {
            Thread.sleep(10);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }

        DepositRequest dep2 = DepositRequest.builder()
                .toWalletId(800L)
                .amount(new BigDecimal("200.00"))
                .currency("KES")
                .idempotencyKey("sort-dep-2")
                .build();
        transactionService.createDeposit(dep2);

        testEntityManager.flush();
        testEntityManager.clear();

        // When: Query sorted by amount ascending
        TransactionListResponse response = transactionService.getTransactions(
                800L, null, null, null, null, 0, 20, "amount", "asc"
        );

        // Then: Transactions should be sorted by amount ascending
        assertThat(response.getTransactions()).hasSizeGreaterThanOrEqualTo(2);
        List<TransactionResponse> transactions = response.getTransactions();
        for (int i = 0; i < transactions.size() - 1; i++) {
            assertThat(transactions.get(i).getAmount())
                    .isLessThanOrEqualTo(transactions.get(i + 1).getAmount());
        }
    }

    @Test
    void getTransactionsShouldUseDefaultPaginationWhenNotProvided() {
        // Setup: Create test wallet
        createTestWallet(900L, new BigDecimal("100000.00"), new BigDecimal("1000000.00"));

        DepositRequest dep1 = DepositRequest.builder()
                .toWalletId(900L)
                .amount(new BigDecimal("100.00"))
                .currency("KES")
                .idempotencyKey("default-pagination-dep-1")
                .build();
        transactionService.createDeposit(dep1);

        testEntityManager.flush();
        testEntityManager.clear();

        // When: Query with null pagination parameters
        TransactionListResponse response = transactionService.getTransactions(
                null, null, null, null, null, null, null, null, null
        );

        // Then: Should use defaults (page 0, size 20)
        assertThat(response.getPagination().getPage()).isEqualTo(0);
        assertThat(response.getPagination().getSize()).isEqualTo(20);
    }

    @Test
    void getTransactionsShouldEnforceMaxPageSize() {
        // Setup: Create test wallet
        createTestWallet(1000L, new BigDecimal("100000.00"), new BigDecimal("1000000.00"));

        DepositRequest dep1 = DepositRequest.builder()
                .toWalletId(1000L)
                .amount(new BigDecimal("100.00"))
                .currency("KES")
                .idempotencyKey("max-size-dep-1")
                .build();
        transactionService.createDeposit(dep1);

        testEntityManager.flush();
        testEntityManager.clear();

        // When: Query with size > 100
        TransactionListResponse response = transactionService.getTransactions(
                null, null, null, null, null, 0, 200, null, null
        );

        // Then: Should cap at 100
        assertThat(response.getPagination().getSize()).isEqualTo(100);
    }

    @Test
    void getTransactionsShouldHandleInvalidSortField() {
        // Setup: Create test wallet
        createTestWallet(1100L, new BigDecimal("100000.00"), new BigDecimal("1000000.00"));

        DepositRequest dep1 = DepositRequest.builder()
                .toWalletId(1100L)
                .amount(new BigDecimal("100.00"))
                .currency("KES")
                .idempotencyKey("invalid-sort-dep-1")
                .build();
        transactionService.createDeposit(dep1);

        testEntityManager.flush();
        testEntityManager.clear();

        // When: Query with invalid sort field
        TransactionListResponse response = transactionService.getTransactions(
                null, null, null, null, null, 0, 20, "invalidField", "asc"
        );

        // Then: Should default to initiatedAt sorting (no exception thrown)
        assertThat(response.getTransactions()).isNotEmpty();
    }

    @Test
    void getTransactionsShouldSupportCombinedFilters() {
        // Setup: Create test wallets
        createTestWallet(1200L, new BigDecimal("100000.00"), new BigDecimal("1000000.00"));
        createTestWallet(1201L, new BigDecimal("100000.00"), new BigDecimal("1000000.00"));

        DepositRequest dep1 = DepositRequest.builder()
                .toWalletId(1200L)
                .amount(new BigDecimal("100.00"))
                .currency("KES")
                .idempotencyKey("combined-filter-dep-1")
                .build();
        transactionService.createDeposit(dep1);

        WithdrawalRequest wd1 = WithdrawalRequest.builder()
                .fromWalletId(1200L)
                .amount(new BigDecimal("50.00"))
                .currency("KES")
                .idempotencyKey("combined-filter-wd-1")
                .build();
        transactionService.createWithdrawal(wd1);

        testEntityManager.flush();
        testEntityManager.clear();

        // When: Query with combined filters (walletId + status + transactionType)
        TransactionListResponse response = transactionService.getTransactions(
                1200L, null, null, TransactionStatus.COMPLETED, TransactionType.DEPOSIT, 0, 20, null, null
        );

        // Then: Should return only matching transactions
        assertThat(response.getTransactions()).isNotEmpty();
        assertThat(response.getTransactions()).allMatch(tx -> 
                "DEPOSIT".equals(tx.getTransactionType()) &&
                "COMPLETED".equals(tx.getStatus()) &&
                (tx.getToWalletId() != null && tx.getToWalletId().equals(1200L) ||
                 tx.getFromWalletId() != null && tx.getFromWalletId().equals(1200L))
        );
    }
}
