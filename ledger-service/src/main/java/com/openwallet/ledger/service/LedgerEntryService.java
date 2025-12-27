package com.openwallet.ledger.service;

import com.openwallet.ledger.domain.LedgerEntry;
import com.openwallet.ledger.dto.LedgerEntryResponse;
import com.openwallet.ledger.repository.LedgerEntryRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Service for querying ledger entries.
 * Used for balance reconciliation and audit purposes.
 */
@Service
@RequiredArgsConstructor
@SuppressWarnings("null")
public class LedgerEntryService {

    private static final DateTimeFormatter DATE_TIME_FORMATTER = DateTimeFormatter.ISO_LOCAL_DATE_TIME;

    private final LedgerEntryRepository ledgerEntryRepository;

    /**
     * Gets all ledger entries for a specific wallet, ordered by creation date (newest first).
     * 
     * @param walletId Wallet ID
     * @return List of ledger entries for the wallet
     */
    @Transactional(readOnly = true)
    public List<LedgerEntryResponse> getLedgerEntriesByWalletId(Long walletId) {
        if (walletId == null) {
            throw new IllegalArgumentException("Wallet ID is required");
        }

        List<LedgerEntry> entries = ledgerEntryRepository.findByWalletIdOrderByCreatedAtDesc(walletId);
        return entries.stream()
                .map(this::toResponse)
                .collect(Collectors.toList());
    }

    /**
     * Calculates the current balance for a wallet by summing all ledger entries.
     * Balance = Sum of CREDITS - Sum of DEBITS
     * 
     * @param walletId Wallet ID
     * @return Calculated balance from ledger entries
     */
    @Transactional(readOnly = true)
    public java.math.BigDecimal calculateBalanceFromLedger(Long walletId) {
        if (walletId == null) {
            throw new IllegalArgumentException("Wallet ID is required");
        }

        List<LedgerEntry> entries = ledgerEntryRepository.findByWalletIdOrderByCreatedAtDesc(walletId);
        
        java.math.BigDecimal balance = java.math.BigDecimal.ZERO;
        for (LedgerEntry entry : entries) {
            if (entry.getEntryType() == com.openwallet.ledger.domain.EntryType.CREDIT) {
                balance = balance.add(entry.getAmount());
            } else if (entry.getEntryType() == com.openwallet.ledger.domain.EntryType.DEBIT) {
                balance = balance.subtract(entry.getAmount());
            }
        }

        return balance;
    }

    private LedgerEntryResponse toResponse(LedgerEntry entry) {
        return LedgerEntryResponse.builder()
                .id(entry.getId())
                .transactionId(entry.getTransaction() != null ? entry.getTransaction().getId() : null)
                .walletId(entry.getWalletId())
                .accountType(entry.getAccountType())
                .entryType(entry.getEntryType() != null ? entry.getEntryType().name() : null)
                .amount(entry.getAmount())
                .balanceAfter(entry.getBalanceAfter())
                .createdAt(entry.getCreatedAt() != null ? entry.getCreatedAt().format(DATE_TIME_FORMATTER) : null)
                .build();
    }
}


