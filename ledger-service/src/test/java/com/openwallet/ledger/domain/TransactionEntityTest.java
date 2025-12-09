package com.openwallet.ledger.domain;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.autoconfigure.orm.jpa.TestEntityManager;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;
import com.openwallet.ledger.config.JpaConfig;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Integration test for Transaction entity to verify JPA mapping and
 * persistence.
 */
@DataJpaTest
@Import(JpaConfig.class)
@ActiveProfiles("test")
class TransactionEntityTest {

    @Autowired
    private TestEntityManager entityManager;

    @Test
    void shouldPersistDepositTransaction() {
        // Given
        Transaction transaction = Transaction.builder()
                .transactionType(TransactionType.DEPOSIT)
                .amount(new BigDecimal("500.00"))
                .currency("KES")
                .toWalletId(1L)
                .status(TransactionStatus.PENDING)
                .idempotencyKey("deposit-123")
                .build();

        // When
        Transaction saved = entityManager.persistAndFlush(transaction);

        // Then
        assertThat(saved.getId()).isNotNull();
        assertThat(saved.getTransactionType()).isEqualTo(TransactionType.DEPOSIT);
        assertThat(saved.getAmount()).isEqualByComparingTo(new BigDecimal("500.00"));
        assertThat(saved.getToWalletId()).isEqualTo(1L);
        assertThat(saved.getFromWalletId()).isNull();
        assertThat(saved.getStatus()).isEqualTo(TransactionStatus.PENDING);
        assertThat(saved.getIdempotencyKey()).isEqualTo("deposit-123");
        assertThat(saved.getCreatedAt()).isNotNull();
    }

    @Test
    void shouldPersistTransferTransaction() {
        // Given
        Transaction transaction = Transaction.builder()
                .transactionType(TransactionType.TRANSFER)
                .amount(new BigDecimal("200.00"))
                .currency("KES")
                .fromWalletId(1L)
                .toWalletId(2L)
                .status(TransactionStatus.PENDING)
                .idempotencyKey("transfer-456")
                .build();

        // When
        Transaction saved = entityManager.persistAndFlush(transaction);

        // Then
        assertThat(saved.getId()).isNotNull();
        assertThat(saved.getTransactionType()).isEqualTo(TransactionType.TRANSFER);
        assertThat(saved.getFromWalletId()).isEqualTo(1L);
        assertThat(saved.getToWalletId()).isEqualTo(2L);
        assertThat(saved.getStatus()).isEqualTo(TransactionStatus.PENDING);
    }

    @Test
    void shouldMaintainRelationshipWithLedgerEntry() {
        // Given
        Transaction transaction = Transaction.builder()
                .transactionType(TransactionType.DEPOSIT)
                .amount(new BigDecimal("100.00"))
                .currency("KES")
                .toWalletId(1L)
                .status(TransactionStatus.COMPLETED)
                .build();

        Transaction savedTransaction = entityManager.persistAndFlush(transaction);

        LedgerEntry entry = LedgerEntry.builder()
                .transaction(savedTransaction)
                .walletId(1L)
                .accountType("WALLET_1")
                .entryType(EntryType.CREDIT)
                .amount(new BigDecimal("100.00"))
                .balanceAfter(new BigDecimal("100.00"))
                .build();

        // When
        LedgerEntry savedEntry = entityManager.persistAndFlush(entry);

        // Then
        assertThat(savedEntry.getId()).isNotNull();
        assertThat(savedEntry.getTransaction().getId()).isEqualTo(savedTransaction.getId());
        assertThat(savedEntry.getEntryType()).isEqualTo(EntryType.CREDIT);
    }
}
