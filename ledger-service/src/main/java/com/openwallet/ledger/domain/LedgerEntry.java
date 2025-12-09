package com.openwallet.ledger.domain;

import jakarta.persistence.*;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.*;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * Ledger entry entity representing a double-entry bookkeeping record.
 * Note: wallet_id references wallets table but is stored as BIGINT (cross-service reference).
 */
@Entity
@Table(name = "ledger_entries", indexes = {
    @Index(name = "idx_ledger_transaction_id", columnList = "transaction_id"),
    @Index(name = "idx_ledger_wallet_id", columnList = "wallet_id"),
    @Index(name = "idx_ledger_account_type", columnList = "account_type"),
    @Index(name = "idx_ledger_created_at", columnList = "created_at DESC")
})
@EntityListeners(AuditingEntityListener.class)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class LedgerEntry {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "transaction_id", nullable = false, foreignKey = @ForeignKey(name = "fk_ledger_transaction"))
    @NotNull(message = "Transaction is required")
    private Transaction transaction;

    @Column(name = "wallet_id")
    private Long walletId; // References wallets table (cross-service reference)

    @Column(name = "account_type", nullable = false, length = 50)
    @NotBlank(message = "Account type is required")
    private String accountType; // WALLET_{walletId}, CASH_ACCOUNT, FEE_ACCOUNT

    @Enumerated(EnumType.STRING)
    @Column(name = "entry_type", nullable = false, length = 10)
    @NotNull(message = "Entry type is required")
    private EntryType entryType; // DEBIT or CREDIT

    @Column(name = "amount", nullable = false, precision = 19, scale = 2)
    @NotNull(message = "Amount is required")
    @DecimalMin(value = "0.01", message = "Amount must be greater than 0")
    private BigDecimal amount;

    @Column(name = "balance_after", nullable = false, precision = 19, scale = 2)
    @NotNull(message = "Balance after is required")
    private BigDecimal balanceAfter; // Running balance after this entry

    @CreatedDate
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;
}

