package com.openwallet.ledger.repository;

import com.openwallet.ledger.domain.Transaction;
import com.openwallet.ledger.domain.TransactionStatus;
import com.openwallet.ledger.domain.TransactionType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

public interface TransactionRepository extends JpaRepository<Transaction, Long> {

    Optional<Transaction> findByIdempotencyKey(String idempotencyKey);

    @Query("select t from Transaction t " +
            "where (:walletId is null or t.fromWalletId = :walletId or t.toWalletId = :walletId) " +
            "order by t.initiatedAt desc")
    List<Transaction> findByWalletOrdered(@Param("walletId") Long walletId);

    List<Transaction> findByStatus(TransactionStatus status);

    List<Transaction> findByTransactionTypeAndInitiatedAtBetween(TransactionType type,
            LocalDateTime from,
            LocalDateTime to);

    /**
     * Calculates the sum of transaction amounts for a wallet within a date range.
     * Includes transactions where the wallet is either the source (fromWalletId) or destination (toWalletId).
     * Only counts COMPLETED transactions.
     * 
     * @param walletId Wallet ID
     * @param from Start date (inclusive)
     * @param to End date (exclusive)
     * @return Sum of transaction amounts
     */
    @Query("SELECT COALESCE(SUM(t.amount), 0) FROM Transaction t " +
           "WHERE (t.fromWalletId = :walletId OR t.toWalletId = :walletId) " +
           "AND t.status = 'COMPLETED' " +
           "AND t.initiatedAt >= :from " +
           "AND t.initiatedAt <= :to")
    BigDecimal sumTransactionAmountsByWalletAndDateRange(
            @Param("walletId") Long walletId,
            @Param("from") LocalDateTime from,
            @Param("to") LocalDateTime to
    );

    /**
     * Finds transactions with optional filtering by wallet, date range, status, and transaction type.
     * Supports pagination and sorting via Pageable.
     * 
     * @param walletId Optional wallet ID filter (matches fromWalletId or toWalletId)
     * @param fromDate Optional start date (inclusive)
     * @param toDate Optional end date (inclusive)
     * @param status Optional status filter
     * @param transactionType Optional transaction type filter
     * @param pageable Pagination and sorting parameters
     * @return Page of transactions matching the criteria
     */
    @Query("SELECT t FROM Transaction t WHERE " +
           "(:walletId IS NULL OR t.fromWalletId = :walletId OR t.toWalletId = :walletId) AND " +
           "(:fromDate IS NULL OR t.initiatedAt >= :fromDate) AND " +
           "(:toDate IS NULL OR t.initiatedAt <= :toDate) AND " +
           "(:status IS NULL OR t.status = :status) AND " +
           "(:transactionType IS NULL OR t.transactionType = :transactionType)")
    Page<Transaction> findTransactionsWithFilters(
            @Param("walletId") Long walletId,
            @Param("fromDate") LocalDateTime fromDate,
            @Param("toDate") LocalDateTime toDate,
            @Param("status") TransactionStatus status,
            @Param("transactionType") TransactionType transactionType,
            Pageable pageable
    );
}
