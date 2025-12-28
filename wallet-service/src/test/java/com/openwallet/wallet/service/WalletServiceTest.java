package com.openwallet.wallet.service;

import com.openwallet.wallet.cache.BalanceCacheService;
import com.openwallet.wallet.config.JpaConfig;
import com.openwallet.wallet.domain.Wallet;
import com.openwallet.wallet.dto.CreateWalletRequest;
import com.openwallet.wallet.dto.TransactionListResponse;
import com.openwallet.wallet.dto.WalletResponse;
import com.openwallet.wallet.exception.WalletAlreadyExistsException;
import com.openwallet.wallet.exception.WalletNotFoundException;
import com.openwallet.wallet.repository.TransactionRepository;
import com.openwallet.wallet.repository.WalletRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DataJpaTest
@Import({ JpaConfig.class, WalletService.class })
@ActiveProfiles("test")
@SuppressWarnings({ "ConstantConditions", "null" })
class WalletServiceTest {

    @Autowired
    private WalletService walletService;

    @Autowired
    private WalletRepository walletRepository;

    @Autowired
    private TransactionRepository transactionRepository;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @MockBean
    private BalanceCacheService balanceCacheService;

    @Test
    void createWalletShouldSucceed() {
        CreateWalletRequest request = CreateWalletRequest.builder()
                .currency("kes")
                .build();

        WalletResponse response = walletService.createWallet(1L, request);

        assertThat(response.getId()).isNotNull();
        assertThat(response.getCustomerId()).isEqualTo(1L);
        assertThat(response.getCurrency()).isEqualTo("KES");
        assertThat(response.getBalance()).isEqualByComparingTo(BigDecimal.ZERO);
    }

    @Test
    void createWalletShouldAllowMultipleKESWallets() {
        // Create first KES wallet
        Optional.ofNullable(walletRepository.save(Wallet.builder().customerId(1L).currency("KES").build()))
                .orElseThrow(() -> new IllegalStateException("Failed to seed wallet"));

        // Create second KES wallet - should succeed
        CreateWalletRequest request = CreateWalletRequest.builder()
                .currency("KES")
                .build();

        WalletResponse response = walletService.createWallet(1L, request);
        
        assertThat(response.getId()).isNotNull();
        assertThat(response.getCurrency()).isEqualTo("KES");
        assertThat(response.getCustomerId()).isEqualTo(1L);
        
        // Verify both wallets exist
        List<Wallet> wallets = walletRepository.findByCustomerId(1L);
        assertThat(wallets).hasSize(2);
        assertThat(wallets).extracting(Wallet::getCurrency).containsOnly("KES");
    }

    @Test
    void createWalletShouldRejectNonKESCurrency() {
        CreateWalletRequest request = CreateWalletRequest.builder()
                .currency("USD")
                .build();

        assertThatThrownBy(() -> walletService.createWallet(1L, request))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Only KES currency is supported");
    }

    @Test
    void createWalletShouldDefaultToKESWhenCurrencyNotProvided() {
        CreateWalletRequest request = CreateWalletRequest.builder()
                .currency(null) // Not provided
                .build();

        WalletResponse response = walletService.createWallet(1L, request);

        assertThat(response.getCurrency()).isEqualTo("KES");
    }

    @Test
    void getWalletShouldReturnOwnedWallet() {
        Wallet saved = Optional
                .ofNullable(walletRepository.save(Wallet.builder().customerId(2L).currency("KES").build()))
                .orElseThrow(() -> new IllegalStateException("Failed to seed wallet"));

        WalletResponse response = walletService.getWallet(saved.getId(), 2L);

        assertThat(response.getId()).isEqualTo(saved.getId());
        assertThat(response.getCurrency()).isEqualTo("KES");
    }

    @Test
    void getWalletShouldFailWhenNotOwned() {
        Wallet saved = Optional
                .ofNullable(walletRepository.save(Wallet.builder().customerId(2L).currency("KES").build()))
                .orElseThrow(() -> new IllegalStateException("Failed to seed wallet"));

        assertThatThrownBy(() -> walletService.getWallet(saved.getId(), 3L))
                .isInstanceOf(WalletNotFoundException.class);
    }

    @Test
    void getMyWalletsShouldReturnOnlyCustomersWallets() {
        // For MVP, only KES is supported. The unique constraint (customer_id, currency)
        // means a customer can only have one KES wallet.
        Optional.ofNullable(walletRepository.save(Wallet.builder().customerId(5L).currency("KES").build()))
                .orElseThrow(() -> new IllegalStateException("Failed to seed wallet"));
        Optional.ofNullable(walletRepository.save(Wallet.builder().customerId(6L).currency("KES").build()))
                .orElseThrow(() -> new IllegalStateException("Failed to seed wallet"));

        List<WalletResponse> wallets = walletService.getMyWallets(5L);

        assertThat(wallets).hasSize(1);
        assertThat(wallets).extracting(WalletResponse::getCurrency)
                .containsExactly("KES");
    }

    // ========== Wallet Transaction History Tests ==========

    private void createTestTransaction(Long id, Long fromWalletId, Long toWalletId, String type, 
                                       String status, BigDecimal amount, LocalDateTime initiatedAt) {
        jdbcTemplate.update(
            "INSERT INTO transactions (id, transaction_type, amount, currency, from_wallet_id, to_wallet_id, " +
            "status, initiated_at, created_at, updated_at) VALUES (?, ?, ?, 'KES', ?, ?, ?, ?, ?, ?)",
            id, type, amount, fromWalletId, toWalletId, status, initiatedAt, LocalDateTime.now(), LocalDateTime.now()
        );
    }

    @Test
    void getWalletTransactionsShouldValidateOwnership() {
        // Setup: Create wallets for different customers
        Wallet wallet1 = walletRepository.save(Wallet.builder().customerId(100L).currency("KES").build());
        Wallet wallet2 = walletRepository.save(Wallet.builder().customerId(200L).currency("KES").build());

        // When: Try to get transactions for wallet owned by different customer
        // Then: Should throw WalletNotFoundException
        assertThatThrownBy(() -> walletService.getWalletTransactions(
                wallet1.getId(), 200L, null, null, null, null, 0, 20, null, null
        )).isInstanceOf(WalletNotFoundException.class);
    }

    @Test
    void getWalletTransactionsShouldReturnTransactionsForOwnedWallet() {
        // Setup: Create wallet and transactions
        Wallet wallet = walletRepository.save(Wallet.builder().customerId(300L).currency("KES").build());
        LocalDateTime now = LocalDateTime.now();

        createTestTransaction(1L, null, wallet.getId(), "DEPOSIT", "COMPLETED", 
                new BigDecimal("100.00"), now);
        createTestTransaction(2L, wallet.getId(), null, "WITHDRAWAL", "COMPLETED", 
                new BigDecimal("50.00"), now.plusMinutes(1));

        // When: Get transactions for wallet
        TransactionListResponse response = walletService.getWalletTransactions(
                wallet.getId(), 300L, null, null, null, null, 0, 20, null, null
        );

        // Then: Should return transactions for the wallet
        assertThat(response.getTransactions()).isNotEmpty();
        assertThat(response.getPagination().getTotalElements()).isGreaterThanOrEqualTo(2);
    }

    @Test
    void getWalletTransactionsShouldFilterByStatus() {
        // Setup: Create wallet and transactions with different statuses
        Wallet wallet = walletRepository.save(Wallet.builder().customerId(400L).currency("KES").build());
        LocalDateTime now = LocalDateTime.now();

        createTestTransaction(3L, null, wallet.getId(), "DEPOSIT", "COMPLETED", 
                new BigDecimal("100.00"), now);
        createTestTransaction(4L, null, wallet.getId(), "DEPOSIT", "PENDING", 
                new BigDecimal("200.00"), now.plusMinutes(1));

        // When: Get only COMPLETED transactions
        TransactionListResponse response = walletService.getWalletTransactions(
                wallet.getId(), 400L, null, null, "COMPLETED", null, 0, 20, null, null
        );

        // Then: Should return only COMPLETED transactions
        assertThat(response.getTransactions()).isNotEmpty();
        assertThat(response.getTransactions()).allMatch(tx -> 
                "COMPLETED".equals(tx.getStatus())
        );
    }

    @Test
    void getWalletTransactionsShouldFilterByTransactionType() {
        // Setup: Create wallet and different transaction types
        Wallet wallet = walletRepository.save(Wallet.builder().customerId(500L).currency("KES").build());
        LocalDateTime now = LocalDateTime.now();

        createTestTransaction(5L, null, wallet.getId(), "DEPOSIT", "COMPLETED", 
                new BigDecimal("100.00"), now);
        createTestTransaction(6L, wallet.getId(), null, "WITHDRAWAL", "COMPLETED", 
                new BigDecimal("50.00"), now.plusMinutes(1));

        // When: Get only DEPOSIT transactions
        TransactionListResponse response = walletService.getWalletTransactions(
                wallet.getId(), 500L, null, null, null, "DEPOSIT", 0, 20, null, null
        );

        // Then: Should return only DEPOSIT transactions
        assertThat(response.getTransactions()).isNotEmpty();
        assertThat(response.getTransactions()).allMatch(tx -> 
                "DEPOSIT".equals(tx.getTransactionType())
        );
    }

    @Test
    void getWalletTransactionsShouldSupportPagination() {
        // Setup: Create wallet and multiple transactions
        Wallet wallet = walletRepository.save(Wallet.builder().customerId(600L).currency("KES").build());
        LocalDateTime now = LocalDateTime.now();

        for (int i = 7; i <= 11; i++) {
            createTestTransaction((long) i, null, wallet.getId(), "DEPOSIT", "COMPLETED", 
                    new BigDecimal("10.00"), now.plusMinutes(i));
        }

        // When: Get first page with size 2
        TransactionListResponse page1 = walletService.getWalletTransactions(
                wallet.getId(), 600L, null, null, null, null, 0, 2, null, null
        );

        // Then: Should return 2 transactions
        assertThat(page1.getTransactions()).hasSize(2);
        assertThat(page1.getPagination().getPage()).isEqualTo(0);
        assertThat(page1.getPagination().getSize()).isEqualTo(2);
        assertThat(page1.getPagination().getTotalElements()).isGreaterThanOrEqualTo(5);
        assertThat(page1.getPagination().isHasNext()).isTrue();
    }

    @Test
    void getWalletTransactionsShouldSupportSorting() {
        // Setup: Create wallet and transactions with different amounts
        Wallet wallet = walletRepository.save(Wallet.builder().customerId(700L).currency("KES").build());
        LocalDateTime now = LocalDateTime.now();

        createTestTransaction(12L, null, wallet.getId(), "DEPOSIT", "COMPLETED", 
                new BigDecimal("100.00"), now);
        createTestTransaction(13L, null, wallet.getId(), "DEPOSIT", "COMPLETED", 
                new BigDecimal("50.00"), now.plusMinutes(1));
        createTestTransaction(14L, null, wallet.getId(), "DEPOSIT", "COMPLETED", 
                new BigDecimal("200.00"), now.plusMinutes(2));

        // When: Get transactions sorted by amount ascending
        TransactionListResponse response = walletService.getWalletTransactions(
                wallet.getId(), 700L, null, null, null, null, 0, 20, "amount", "asc"
        );

        // Then: Transactions should be sorted by amount
        assertThat(response.getTransactions()).hasSizeGreaterThanOrEqualTo(3);
        List<TransactionListResponse.TransactionItem> transactions = response.getTransactions();
        for (int i = 0; i < transactions.size() - 1; i++) {
            assertThat(transactions.get(i).getAmount())
                    .isLessThanOrEqualTo(transactions.get(i + 1).getAmount());
        }
    }

    @Test
    void getWalletTransactionsShouldUseDefaultPagination() {
        // Setup: Create wallet and transaction
        Wallet wallet = walletRepository.save(Wallet.builder().customerId(800L).currency("KES").build());
        LocalDateTime now = LocalDateTime.now();

        createTestTransaction(15L, null, wallet.getId(), "DEPOSIT", "COMPLETED", 
                new BigDecimal("100.00"), now);

        // When: Get transactions with null pagination
        TransactionListResponse response = walletService.getWalletTransactions(
                wallet.getId(), 800L, null, null, null, null, null, null, null, null
        );

        // Then: Should use defaults (page 0, size 20)
        assertThat(response.getPagination().getPage()).isEqualTo(0);
        assertThat(response.getPagination().getSize()).isEqualTo(20);
    }

    @Test
    void getWalletTransactionsShouldEnforceMaxPageSize() {
        // Setup: Create wallet and transaction
        Wallet wallet = walletRepository.save(Wallet.builder().customerId(900L).currency("KES").build());
        LocalDateTime now = LocalDateTime.now();

        createTestTransaction(16L, null, wallet.getId(), "DEPOSIT", "COMPLETED", 
                new BigDecimal("100.00"), now);

        // When: Get transactions with size > 100
        TransactionListResponse response = walletService.getWalletTransactions(
                wallet.getId(), 900L, null, null, null, null, 0, 200, null, null
        );

        // Then: Should cap at 100
        assertThat(response.getPagination().getSize()).isEqualTo(100);
    }

    @Test
    void getWalletTransactionsShouldHandleInvalidSortField() {
        // Setup: Create wallet and transaction
        Wallet wallet = walletRepository.save(Wallet.builder().customerId(1000L).currency("KES").build());
        LocalDateTime now = LocalDateTime.now();

        createTestTransaction(17L, null, wallet.getId(), "DEPOSIT", "COMPLETED", 
                new BigDecimal("100.00"), now);

        // When: Get transactions with invalid sort field
        TransactionListResponse response = walletService.getWalletTransactions(
                wallet.getId(), 1000L, null, null, null, null, 0, 20, "invalidField", "asc"
        );

        // Then: Should default to initiatedAt sorting (no exception)
        assertThat(response.getTransactions()).isNotEmpty();
    }

    @Test
    void getWalletTransactionsShouldSupportCombinedFilters() {
        // Setup: Create wallet and transactions
        Wallet wallet = walletRepository.save(Wallet.builder().customerId(1100L).currency("KES").build());
        LocalDateTime now = LocalDateTime.now();

        createTestTransaction(18L, null, wallet.getId(), "DEPOSIT", "COMPLETED", 
                new BigDecimal("100.00"), now);
        createTestTransaction(19L, null, wallet.getId(), "DEPOSIT", "PENDING", 
                new BigDecimal("200.00"), now.plusMinutes(1));
        createTestTransaction(20L, wallet.getId(), null, "WITHDRAWAL", "COMPLETED", 
                new BigDecimal("50.00"), now.plusMinutes(2));

        // When: Get transactions with combined filters (status + transactionType)
        TransactionListResponse response = walletService.getWalletTransactions(
                wallet.getId(), 1100L, null, null, "COMPLETED", "DEPOSIT", 0, 20, null, null
        );

        // Then: Should return only matching transactions
        assertThat(response.getTransactions()).isNotEmpty();
        assertThat(response.getTransactions()).allMatch(tx -> 
                "DEPOSIT".equals(tx.getTransactionType()) &&
                "COMPLETED".equals(tx.getStatus())
        );
    }
}
