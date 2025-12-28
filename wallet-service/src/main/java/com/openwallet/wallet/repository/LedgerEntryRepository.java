package com.openwallet.wallet.repository;

import com.openwallet.wallet.domain.LedgerEntry;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.math.BigDecimal;
import java.util.List;

/**
 * Read-only repository for accessing ledger_entries table.
 * This is a cross-service read access for balance reconciliation purposes.
 * 
 * Note: This is a read-only view - wallet service should never write to ledger_entries.
 * The ledger service owns this table and is the source of truth.
 * 
 * The LedgerEntry entity is marked as @Immutable to prevent write operations.
 */
public interface LedgerEntryRepository extends JpaRepository<LedgerEntry, Long> {

    /**
     * Finds all ledger entries for a wallet, ordered by creation date (newest first).
     * 
     * @param walletId Wallet ID
     * @return List of ledger entries
     */
    @Query("SELECT le FROM LedgerEntry le WHERE le.walletId = :walletId ORDER BY le.createdAt DESC")
    List<LedgerEntry> findByWalletIdOrderByCreatedAtDesc(@Param("walletId") Long walletId);

    /**
     * Calculates balance from ledger entries for a wallet.
     * Balance = Sum of CREDITS - Sum of DEBITS
     * 
     * @param walletId Wallet ID
     * @return Calculated balance
     */
    @Query("SELECT COALESCE(SUM(CASE WHEN le.entryType = 'CREDIT' THEN le.amount ELSE -le.amount END), 0) " +
           "FROM LedgerEntry le WHERE le.walletId = :walletId")
    BigDecimal calculateBalanceFromLedger(@Param("walletId") Long walletId);
}

