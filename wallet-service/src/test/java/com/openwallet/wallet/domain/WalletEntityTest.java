package com.openwallet.wallet.domain;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.autoconfigure.orm.jpa.TestEntityManager;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;
import com.openwallet.wallet.config.JpaConfig;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Integration test for Wallet entity to verify JPA mapping and persistence.
 */
@DataJpaTest
@Import(JpaConfig.class)
@ActiveProfiles("test")
class WalletEntityTest {

    @Autowired
    private TestEntityManager entityManager;

    @Test
    void shouldPersistWalletWithAllFields() {
        // Given
        Wallet wallet = Wallet.builder()
                .customerId(1L)
                .currency("KES")
                .status(WalletStatus.ACTIVE)
                .balance(new BigDecimal("1000.00"))
                .dailyLimit(new BigDecimal("100000.00"))
                .monthlyLimit(new BigDecimal("1000000.00"))
                .build();

        // When
        Wallet saved = entityManager.persistAndFlush(wallet);

        // Then
        assertThat(saved.getId()).isNotNull();
        assertThat(saved.getCustomerId()).isEqualTo(1L);
        assertThat(saved.getCurrency()).isEqualTo("KES");
        assertThat(saved.getStatus()).isEqualTo(WalletStatus.ACTIVE);
        assertThat(saved.getBalance()).isEqualByComparingTo(new BigDecimal("1000.00"));
        assertThat(saved.getDailyLimit()).isEqualByComparingTo(new BigDecimal("100000.00"));
        assertThat(saved.getMonthlyLimit()).isEqualByComparingTo(new BigDecimal("1000000.00"));
        assertThat(saved.getCreatedAt()).isNotNull();
        assertThat(saved.getUpdatedAt()).isNotNull();
    }

    @Test
    void shouldSetDefaultValues() {
        // Given
        Wallet wallet = Wallet.builder()
                .customerId(1L)
                .build();

        // When
        Wallet saved = entityManager.persistAndFlush(wallet);

        // Then
        assertThat(saved.getCurrency()).isEqualTo("KES");
        assertThat(saved.getStatus()).isEqualTo(WalletStatus.ACTIVE);
        assertThat(saved.getBalance()).isEqualByComparingTo(BigDecimal.ZERO);
        assertThat(saved.getDailyLimit()).isEqualByComparingTo(new BigDecimal("100000.00"));
        assertThat(saved.getMonthlyLimit()).isEqualByComparingTo(new BigDecimal("1000000.00"));
    }
}

