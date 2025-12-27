package com.openwallet.ledger.controller;

import com.openwallet.ledger.dto.LedgerEntryResponse;
import com.openwallet.ledger.service.LedgerEntryService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.math.BigDecimal;
import java.util.List;

/**
 * Controller for querying ledger entries.
 * Used for balance reconciliation and audit purposes.
 */
@RestController
@RequestMapping("/api/v1/ledger-entries")
@RequiredArgsConstructor
public class LedgerEntryController {

    private final LedgerEntryService ledgerEntryService;

    /**
     * Gets all ledger entries for a specific wallet.
     *
     * @param walletId Wallet ID
     * @return List of ledger entries for the wallet
     */
    @GetMapping("/wallet/{walletId}")
    @PreAuthorize("hasAnyRole('USER', 'ADMIN', 'AUDITOR')")
    public ResponseEntity<List<LedgerEntryResponse>> getLedgerEntriesByWalletId(
            @PathVariable Long walletId) {
        List<LedgerEntryResponse> entries = ledgerEntryService.getLedgerEntriesByWalletId(walletId);
        return ResponseEntity.ok(entries);
    }

    /**
     * Calculates the current balance for a wallet from ledger entries.
     * Balance = Sum of CREDITS - Sum of DEBITS
     *
     * @param walletId Wallet ID
     * @return Calculated balance from ledger entries
     */
    @GetMapping("/wallet/{walletId}/balance")
    @PreAuthorize("hasAnyRole('USER', 'ADMIN', 'AUDITOR')")
    public ResponseEntity<BalanceCalculationResponse> calculateBalanceFromLedger(
            @PathVariable Long walletId) {
        BigDecimal balance = ledgerEntryService.calculateBalanceFromLedger(walletId);
        return ResponseEntity.ok(new BalanceCalculationResponse(walletId, balance));
    }

    /**
     * Response DTO for balance calculation.
     */
    public record BalanceCalculationResponse(Long walletId, BigDecimal balance) {

    }
}


