package com.openwallet.wallet.repository;

import com.openwallet.wallet.domain.Transaction;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;

/**
 * Read-only repository for accessing transactions table.
 * This is a cross-service read access for transaction history purposes.
 * 
 * Note: This is a read-only view - wallet service should never write to transactions.
 * The ledger service owns this table and is the source of truth.
 * 
 * The Transaction entity is marked as @Immutable to prevent write operations.
 */
public interface TransactionRepository extends JpaRepository<Transaction, Long> {

    /**
     * Finds transactions with optional filtering by wallet, date range, status, and transaction type.
     * Supports pagination and sorting via Pageable.
     * 
     * Note: Service layer should pass non-null default dates when filters are not provided
     * to avoid PostgreSQL type inference issues.
     * 
     * @param walletId Optional wallet ID filter (matches fromWalletId or toWalletId)
     * @param fromDate Start date (inclusive) - should not be null (use LocalDateTime.MIN if no filter)
     * @param toDate End date (inclusive) - should not be null (use LocalDateTime.MAX if no filter)
     * @param status Optional status filter (as String to avoid enum conflicts)
     * @param transactionType Optional transaction type filter (as String to avoid enum conflicts)
     * @param pageable Pagination and sorting parameters
     * @return Page of transactions matching the criteria
     */
    @Query("SELECT t FROM Transaction t WHERE " +
           "(:walletId IS NULL OR t.fromWalletId = :walletId OR t.toWalletId = :walletId) AND " +
           "t.initiatedAt >= :fromDate AND " +
           "t.initiatedAt <= :toDate AND " +
           "(:status IS NULL OR t.status = :status) AND " +
           "(:transactionType IS NULL OR t.transactionType = :transactionType)")
    Page<Transaction> findTransactionsWithFilters(
            @Param("walletId") Long walletId,
            @Param("fromDate") LocalDateTime fromDate,
            @Param("toDate") LocalDateTime toDate,
            @Param("status") String status,
            @Param("transactionType") String transactionType,
            Pageable pageable
    );
}

