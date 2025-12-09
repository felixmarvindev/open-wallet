package com.openwallet.ledger.repository;

import com.openwallet.ledger.domain.LedgerEntry;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface LedgerEntryRepository extends JpaRepository<LedgerEntry, Long> {
    List<LedgerEntry> findByTransactionId(Long transactionId);

    List<LedgerEntry> findByWalletIdOrderByCreatedAtDesc(Long walletId);
}
