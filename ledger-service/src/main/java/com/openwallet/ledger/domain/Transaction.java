package com.openwallet.ledger.domain;

import jakarta.persistence.*;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Transaction entity representing financial transactions (deposits, withdrawals, transfers).
 * Note: from_wallet_id and to_wallet_id reference wallets table but are stored as BIGINT (cross-service references).
 */
@Entity
@Table(name = "transactions", indexes = {
    @Index(name = "idx_transactions_from_wallet", columnList = "from_wallet_id"),
    @Index(name = "idx_transactions_to_wallet", columnList = "to_wallet_id"),
    @Index(name = "idx_transactions_status", columnList = "status"),
    @Index(name = "idx_transactions_initiated_at", columnList = "initiated_at DESC"),
    @Index(name = "idx_transactions_idempotency", columnList = "idempotency_key"),
    @Index(name = "idx_transactions_customer_lookup", columnList = "from_wallet_id, to_wallet_id, initiated_at DESC")
})
@EntityListeners(AuditingEntityListener.class)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Transaction {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Enumerated(EnumType.STRING)
    @Column(name = "transaction_type", nullable = false, length = 20)
    @NotNull(message = "Transaction type is required")
    private TransactionType transactionType;

    @Column(name = "amount", nullable = false, precision = 19, scale = 2)
    @NotNull(message = "Amount is required")
    @DecimalMin(value = "0.01", message = "Amount must be greater than 0")
    private BigDecimal amount;

    @Column(name = "currency", nullable = false, length = 3)
    @NotBlank(message = "Currency is required")
    @Size(min = 3, max = 3, message = "Currency must be 3 characters (ISO code)")
    @Builder.Default
    private String currency = "KES";

    @Column(name = "from_wallet_id")
    private Long fromWalletId; // References wallets table (cross-service reference)

    @Column(name = "to_wallet_id")
    private Long toWalletId; // References wallets table (cross-service reference)

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    @NotNull(message = "Status is required")
    @Builder.Default
    private TransactionStatus status = TransactionStatus.PENDING;

    @Column(name = "initiated_at", nullable = false, updatable = false)
    @Builder.Default
    private LocalDateTime initiatedAt = LocalDateTime.now();

    @Column(name = "completed_at")
    private LocalDateTime completedAt;

    @Column(name = "failure_reason", columnDefinition = "TEXT")
    private String failureReason;

    @Column(name = "idempotency_key", unique = true, length = 255)
    private String idempotencyKey; // For idempotent operations

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "metadata")
    private Map<String, Object> metadata; // Additional transaction metadata

    @OneToMany(mappedBy = "transaction", cascade = CascadeType.ALL, orphanRemoval = true)
    @Builder.Default
    private List<LedgerEntry> ledgerEntries = new ArrayList<>();

    @CreatedDate
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @LastModifiedDate
    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;
}

