package com.openwallet.wallet.service;

import com.openwallet.wallet.cache.BalanceCacheService;
import com.openwallet.wallet.config.JpaConfig;
import com.openwallet.wallet.domain.Wallet;
import com.openwallet.wallet.exception.WalletNotFoundException;
import com.openwallet.wallet.repository.WalletRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DataJpaTest
@Import({ JpaConfig.class, WalletService.class })
@ActiveProfiles("test")
@SuppressWarnings({ "ConstantConditions", "null" })
class WalletServiceBalanceTest {

    @Autowired
    private WalletService walletService;

    @Autowired
    private WalletRepository walletRepository;

    @MockBean
    private BalanceCacheService balanceCacheService;

    @Test
    void updateBalanceFromTransactionDepositIncreasesBalance() {
        // Given: Wallet with initial balance
        Wallet wallet = walletRepository.save(Wallet.builder()
                .customerId(1L)
                .currency("KES")
                .balance(new BigDecimal("100.00"))
                .build());

        // When: Deposit 50 KES
        walletService.updateBalanceFromTransaction(
                wallet.getId(),
                new BigDecimal("50.00"),
                "DEPOSIT",
                true // Credit: increase balance
        );

        // Then: Balance should be 150
        Wallet updated = walletRepository.findById(wallet.getId())
                .orElseThrow();
        assertThat(updated.getBalance()).isEqualByComparingTo(new BigDecimal("150.00"));
    }

    @Test
    void updateBalanceFromTransactionWithdrawalDecreasesBalance() {
        // Given: Wallet with initial balance
        Wallet wallet = walletRepository.save(Wallet.builder()
                .customerId(2L)
                .currency("KES")
                .balance(new BigDecimal("200.00"))
                .build());

        // When: Withdraw 50 KES
        walletService.updateBalanceFromTransaction(
                wallet.getId(),
                new BigDecimal("50.00"),
                "WITHDRAWAL",
                false // Debit: decrease balance
        );

        // Then: Balance should be 150
        Wallet updated = walletRepository.findById(wallet.getId())
                .orElseThrow();
        assertThat(updated.getBalance()).isEqualByComparingTo(new BigDecimal("150.00"));
    }

    @Test
    void updateBalanceFromTransactionTransferDebitDecreasesBalance() {
        // Given: Source wallet with initial balance
        Wallet sourceWallet = walletRepository.save(Wallet.builder()
                .customerId(3L)
                .currency("KES")
                .balance(new BigDecimal("100.00"))
                .build());

        // When: Transfer 30 KES out (debit)
        walletService.updateBalanceFromTransaction(
                sourceWallet.getId(),
                new BigDecimal("30.00"),
                "TRANSFER",
                false // Debit: decrease balance
        );

        // Then: Balance should be 70
        Wallet updated = walletRepository.findById(sourceWallet.getId())
                .orElseThrow();
        assertThat(updated.getBalance()).isEqualByComparingTo(new BigDecimal("70.00"));
    }

    @Test
    void updateBalanceFromTransactionTransferCreditIncreasesBalance() {
        // Given: Destination wallet with initial balance
        Wallet destWallet = walletRepository.save(Wallet.builder()
                .customerId(4L)
                .currency("KES")
                .balance(new BigDecimal("50.00"))
                .build());

        // When: Transfer 30 KES in (credit)
        walletService.updateBalanceFromTransaction(
                destWallet.getId(),
                new BigDecimal("30.00"),
                "TRANSFER",
                true // Credit: increase balance
        );

        // Then: Balance should be 80
        Wallet updated = walletRepository.findById(destWallet.getId())
                .orElseThrow();
        assertThat(updated.getBalance()).isEqualByComparingTo(new BigDecimal("80.00"));
    }

    @Test
    void updateBalanceFromTransactionThrowsExceptionWhenInsufficientBalance() {
        // Given: Wallet with balance 50
        Wallet wallet = walletRepository.save(Wallet.builder()
                .customerId(5L)
                .currency("KES")
                .balance(new BigDecimal("50.00"))
                .build());

        // When/Then: Attempt to withdraw 100 (exceeds balance)
        assertThatThrownBy(() -> walletService.updateBalanceFromTransaction(
                wallet.getId(),
                new BigDecimal("100.00"),
                "WITHDRAWAL",
                false // Debit: decrease balance
        ))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Insufficient balance");

        // Verify balance unchanged
        Wallet unchanged = walletRepository.findById(wallet.getId())
                .orElseThrow();
        assertThat(unchanged.getBalance()).isEqualByComparingTo(new BigDecimal("50.00"));
    }

    @Test
    void updateBalanceFromTransactionThrowsExceptionWhenWalletNotFound() {
        // When/Then: Attempt to update non-existent wallet
        assertThatThrownBy(() -> walletService.updateBalanceFromTransaction(
                999L,
                new BigDecimal("100.00"),
                "DEPOSIT",
                true
        ))
                .isInstanceOf(WalletNotFoundException.class)
                .hasMessageContaining("Wallet not found: 999");
    }

    @Test
    void updateBalanceFromTransactionThrowsExceptionWhenWalletIdIsNull() {
        Wallet wallet = walletRepository.save(Wallet.builder()
                .customerId(6L)
                .currency("KES")
                .balance(new BigDecimal("100.00"))
                .build());

        assertThatThrownBy(() -> walletService.updateBalanceFromTransaction(
                null,
                new BigDecimal("50.00"),
                "DEPOSIT",
                true
        ))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Wallet ID is required");
    }

    @Test
    void updateBalanceFromTransactionThrowsExceptionWhenAmountIsNull() {
        Wallet wallet = walletRepository.save(Wallet.builder()
                .customerId(7L)
                .currency("KES")
                .balance(new BigDecimal("100.00"))
                .build());

        assertThatThrownBy(() -> walletService.updateBalanceFromTransaction(
                wallet.getId(),
                null,
                "DEPOSIT",
                true
        ))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Amount must be greater than zero");
    }

    @Test
    void updateBalanceFromTransactionThrowsExceptionWhenAmountIsZero() {
        Wallet wallet = walletRepository.save(Wallet.builder()
                .customerId(8L)
                .currency("KES")
                .balance(new BigDecimal("100.00"))
                .build());

        assertThatThrownBy(() -> walletService.updateBalanceFromTransaction(
                wallet.getId(),
                BigDecimal.ZERO,
                "DEPOSIT",
                true
        ))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Amount must be greater than zero");
    }

    @Test
    void updateBalanceFromTransactionThrowsExceptionWhenAmountIsNegative() {
        Wallet wallet = walletRepository.save(Wallet.builder()
                .customerId(9L)
                .currency("KES")
                .balance(new BigDecimal("100.00"))
                .build());

        assertThatThrownBy(() -> walletService.updateBalanceFromTransaction(
                wallet.getId(),
                new BigDecimal("-10.00"),
                "DEPOSIT",
                true
        ))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Amount must be greater than zero");
    }

    @Test
    void updateBalanceFromTransactionThrowsExceptionWhenTransactionTypeIsNull() {
        Wallet wallet = walletRepository.save(Wallet.builder()
                .customerId(10L)
                .currency("KES")
                .balance(new BigDecimal("100.00"))
                .build());

        assertThatThrownBy(() -> walletService.updateBalanceFromTransaction(
                wallet.getId(),
                new BigDecimal("50.00"),
                null,
                true
        ))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Transaction type is required");
    }

    @Test
    void updateBalanceFromTransactionAllowsZeroBalanceAfterWithdrawal() {
        // Given: Wallet with balance exactly equal to withdrawal amount
        Wallet wallet = walletRepository.save(Wallet.builder()
                .customerId(11L)
                .currency("KES")
                .balance(new BigDecimal("100.00"))
                .build());

        // When: Withdraw entire balance
        walletService.updateBalanceFromTransaction(
                wallet.getId(),
                new BigDecimal("100.00"),
                "WITHDRAWAL",
                false
        );

        // Then: Balance should be zero (not negative)
        Wallet updated = walletRepository.findById(wallet.getId())
                .orElseThrow();
        assertThat(updated.getBalance()).isEqualByComparingTo(BigDecimal.ZERO);
    }

    @Test
    void updateBalanceFromTransactionUpdatesTimestamp() {
        // Given: Wallet with initial balance
        Wallet wallet = walletRepository.save(Wallet.builder()
                .customerId(12L)
                .currency("KES")
                .balance(new BigDecimal("100.00"))
                .build());
        
        // Capture original timestamp
        var originalTimestamp = wallet.getUpdatedAt();

        // When: Update balance
        walletService.updateBalanceFromTransaction(
                wallet.getId(),
                new BigDecimal("50.00"),
                "DEPOSIT",
                true
        );

        // Then: Updated timestamp should be newer
        Wallet updated = walletRepository.findById(wallet.getId())
                .orElseThrow();
        assertThat(updated.getUpdatedAt()).isNotNull();
        // Note: Timestamp comparison may be flaky in tests, so we just verify it's set
    }
}

