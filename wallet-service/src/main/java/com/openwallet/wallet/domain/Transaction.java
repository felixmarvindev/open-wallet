package com.openwallet.wallet.domain;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.Immutable;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Map;

/**
 * Read-only entity for accessing transactions table from wallet service.
 * 
 * This is a cross-service read access for transaction history purposes.
 * The ledger service owns this table and is the source of truth.
 * 
 * Note: Wallet service should NEVER write to this table - it's read-only.
 * 
 * IMPORTANT FOR TESTING WITH ddl-auto=update/create:
 * 
 * When both services share the same database in integration tests:
 * 1. Ledger service starts first → Hibernate creates/updates table based on its entity
 * 2. Wallet service starts second → Hibernate sees this entity mapping to the same table
 * 
 * To prevent schema conflicts:
 * - @Immutable tells Hibernate this entity is read-only (won't try to alter table)
 * - All columns have insertable=false, updatable=false (extra protection)
 * - Entity structure matches ledger service's entity (same columns, types, indexes)
 * - Uses String for enums (not enum types) to avoid enum class conflicts
 * - No relationships (avoids foreign key conflicts)
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
@Immutable  // Critical: Prevents Hibernate from trying to create/alter this table
@EntityListeners(AuditingEntityListener.class)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Transaction {

    @Id
    @Column(name = "id", insertable = false, updatable = false)
    private Long id;

    // Using String instead of enum to match database storage and avoid enum class conflicts
    // The ledger service uses @Enumerated(EnumType.STRING) which stores as VARCHAR
    @Column(name = "transaction_type", nullable = false, length = 20, insertable = false, updatable = false)
    private String transactionType; // "DEPOSIT", "WITHDRAWAL", "TRANSFER"

    @Column(name = "amount", nullable = false, precision = 19, scale = 2, insertable = false, updatable = false)
    private BigDecimal amount;

    @Column(name = "currency", nullable = false, length = 3, insertable = false, updatable = false)
    private String currency;

    @Column(name = "from_wallet_id", insertable = false, updatable = false)
    private Long fromWalletId; // References wallets table (cross-service reference)

    @Column(name = "to_wallet_id", insertable = false, updatable = false)
    private Long toWalletId; // References wallets table (cross-service reference)

    // Using String instead of enum to match database storage
    @Column(name = "status", nullable = false, length = 20, insertable = false, updatable = false)
    private String status; // "PENDING", "COMPLETED", "FAILED", "CANCELLED"

    @Column(name = "initiated_at", nullable = false, insertable = false, updatable = false)
    private LocalDateTime initiatedAt;

    @Column(name = "completed_at", insertable = false, updatable = false)
    private LocalDateTime completedAt;

    @Column(name = "failure_reason", columnDefinition = "TEXT", insertable = false, updatable = false)
    private String failureReason;

    @Column(name = "idempotency_key", length = 255, insertable = false, updatable = false)
    private String idempotencyKey;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "metadata", insertable = false, updatable = false)
    private Map<String, Object> metadata;

    @Column(name = "created_at", nullable = false, insertable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false, insertable = false, updatable = false)
    private LocalDateTime updatedAt;
}

