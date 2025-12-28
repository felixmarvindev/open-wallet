package com.openwallet.wallet.domain;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.Immutable;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * Read-only entity for accessing ledger_entries table from wallet service.
 * 
 * This is a cross-service read access for balance reconciliation purposes.
 * The ledger service owns this table and is the source of truth.
 * 
 * Note: Wallet service should NEVER write to this table - it's read-only.
 * 
 * IMPORTANT FOR TESTING WITH ddl-auto=update/create:
 * 
 * When both services share the same database in integration tests:
 * 1. Ledger service starts first → Hibernate creates/updates table based on its entity
 *    (with @ManyToOne relationship, foreign keys, etc.)
 * 2. Wallet service starts second → Hibernate sees this entity mapping to the same table
 * 
 * To prevent schema conflicts:
 * - @Immutable tells Hibernate this entity is read-only (won't try to alter table)
 * - All columns have insertable=false, updatable=false (extra protection)
 * - Entity structure matches ledger service's entity (same columns, types, indexes)
 * - Uses String for entryType (not enum) to avoid enum class conflicts
 * - No @ManyToOne relationship (avoids foreign key conflicts)
 * 
 * Hibernate behavior:
 * - With @Immutable: Hibernate will NOT try to create/alter the table
 * - It will only validate that the table structure matches (if ddl-auto=validate)
 * - With ddl-auto=update: Hibernate will skip this entity for schema generation
 * 
 * If you see schema conflicts, ensure:
 * 1. Ledger service starts before wallet service (or use ddl-auto=validate for wallet service)
 * 2. Both entities have matching column definitions
 * 3. The table is created by ledger service's Flyway migrations in production
 */
@Entity
@Table(name = "ledger_entries", indexes = {
        @Index(name = "idx_ledger_transaction_id", columnList = "transaction_id"),
        @Index(name = "idx_ledger_wallet_id", columnList = "wallet_id"),
        @Index(name = "idx_ledger_account_type", columnList = "account_type"),
        @Index(name = "idx_ledger_created_at", columnList = "created_at DESC")
})
@Immutable  // Critical: Prevents Hibernate from trying to create/alter this table
@EntityListeners(AuditingEntityListener.class)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class LedgerEntry {

    @Id
    @Column(name = "id", insertable = false, updatable = false)
    private Long id;

    // Note: Using simple column instead of @ManyToOne to avoid loading Transaction entity
    // This matches the database structure (transaction_id is a BIGINT, not a foreign key in wallet service context)
    @Column(name = "transaction_id", nullable = false, insertable = false, updatable = false)
    private Long transactionId;

    @Column(name = "wallet_id", insertable = false, updatable = false)
    private Long walletId;

    @Column(name = "account_type", nullable = false, length = 50, insertable = false, updatable = false)
    private String accountType;

    // Using String instead of enum to match database storage and avoid enum class conflicts
    // The ledger service uses @Enumerated(EnumType.STRING) which stores as VARCHAR
    @Column(name = "entry_type", nullable = false, length = 10, insertable = false, updatable = false)
    private String entryType; // "DEBIT" or "CREDIT"

    @Column(name = "amount", nullable = false, precision = 19, scale = 2, insertable = false, updatable = false)
    private BigDecimal amount;

    @Column(name = "balance_after", nullable = false, precision = 19, scale = 2, insertable = false, updatable = false)
    private BigDecimal balanceAfter;

    @Column(name = "created_at", nullable = false, insertable = false, updatable = false)
    private LocalDateTime createdAt;
}
