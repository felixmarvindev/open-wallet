package com.openwallet.wallet.domain;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * Read-only entity for accessing ledger_entries table from wallet service.
 * 
 * This is a cross-service read access for balance reconciliation purposes.
 * The ledger service owns this table and is the source of truth.
 * 
 * Note: Wallet service should NEVER write to this table - it's read-only.
 */
@Entity
@Table(name = "ledger_entries")
@Getter
@Setter
@NoArgsConstructor
public class LedgerEntry {

    @Id
    @Column(name = "id")
    private Long id;

    @Column(name = "transaction_id", nullable = false)
    private Long transactionId;

    @Column(name = "wallet_id")
    private Long walletId;

    @Column(name = "account_type", nullable = false, length = 50)
    private String accountType;

    @Column(name = "entry_type", nullable = false, length = 10)
    private String entryType; // "DEBIT" or "CREDIT"

    @Column(name = "amount", nullable = false, precision = 19, scale = 2)
    private BigDecimal amount;

    @Column(name = "balance_after", nullable = false, precision = 19, scale = 2)
    private BigDecimal balanceAfter;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;
}


