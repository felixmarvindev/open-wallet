package com.openwallet.ledger.repository;

import com.openwallet.ledger.domain.Transaction;
import com.openwallet.ledger.domain.TransactionStatus;
import com.openwallet.ledger.domain.TransactionType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

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
}
