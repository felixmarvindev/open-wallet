package com.openwallet.ledger.service;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.jdbc.Sql;

import java.math.BigDecimal;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DataJpaTest
@Import({ com.openwallet.ledger.config.JpaConfig.class, WalletStatusService.class })
@ActiveProfiles("test")
@Sql(scripts = "/sql/create_wallets_table.sql", executionPhase = Sql.ExecutionPhase.BEFORE_TEST_METHOD)
@SuppressWarnings("null")
class WalletStatusServiceTest {

    @Autowired
    private WalletStatusService walletStatusService;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    private void createTestWallet(Long walletId, String status) {
        jdbcTemplate.update(
            "INSERT INTO wallets (id, customer_id, currency, balance, daily_limit, monthly_limit, status) " +
            "VALUES (?, 1, 'KES', 0.00, 100000.00, 1000000.00, ?)",
            walletId, status
        );
    }

    @Test
    void getWalletStatusShouldReturnStatusForActiveWallet() {
        // Given: Active wallet
        createTestWallet(1L, "ACTIVE");

        // When: Getting wallet status
        Optional<String> status = walletStatusService.getWalletStatus(1L);

        // Then: Should return ACTIVE
        assertThat(status).isPresent();
        assertThat(status.get()).isEqualTo("ACTIVE");
    }

    @Test
    void getWalletStatusShouldReturnStatusForSuspendedWallet() {
        // Given: Suspended wallet
        createTestWallet(2L, "SUSPENDED");

        // When: Getting wallet status
        Optional<String> status = walletStatusService.getWalletStatus(2L);

        // Then: Should return SUSPENDED
        assertThat(status).isPresent();
        assertThat(status.get()).isEqualTo("SUSPENDED");
    }

    @Test
    void getWalletStatusShouldReturnEmptyForNonExistentWallet() {
        // When: Getting status for non-existent wallet
        Optional<String> status = walletStatusService.getWalletStatus(999L);

        // Then: Should return empty
        assertThat(status).isEmpty();
    }

    @Test
    void validateWalletActiveShouldSucceedForActiveWallet() {
        // Given: Active wallet
        createTestWallet(3L, "ACTIVE");

        // When/Then: Should not throw
        walletStatusService.validateWalletActive(3L);
    }

    @Test
    void validateWalletActiveShouldThrowForSuspendedWallet() {
        // Given: Suspended wallet
        createTestWallet(4L, "SUSPENDED");

        // When/Then: Should throw
        assertThatThrownBy(() -> walletStatusService.validateWalletActive(4L))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("SUSPENDED")
                .hasMessageContaining("cannot process transactions");
    }

    @Test
    void validateWalletActiveShouldThrowForClosedWallet() {
        // Given: Closed wallet
        createTestWallet(5L, "CLOSED");

        // When/Then: Should throw
        assertThatThrownBy(() -> walletStatusService.validateWalletActive(5L))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("CLOSED")
                .hasMessageContaining("cannot process transactions");
    }

    @Test
    void validateWalletActiveShouldThrowForNonExistentWallet() {
        // When/Then: Should throw
        assertThatThrownBy(() -> walletStatusService.validateWalletActive(999L))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Wallet not found");
    }

    @Test
    void validateWalletActiveShouldAllowNullWalletId() {
        // When/Then: Should not throw for null (cash account transactions)
        walletStatusService.validateWalletActive(null);
    }
}

