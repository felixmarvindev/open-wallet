package com.openwallet.ledger.controller;

import com.openwallet.ledger.domain.TransactionStatus;
import com.openwallet.ledger.domain.TransactionType;
import com.openwallet.ledger.dto.DepositRequest;
import com.openwallet.ledger.dto.TransactionListResponse;
import com.openwallet.ledger.dto.TransactionResponse;
import com.openwallet.ledger.dto.TransferRequest;
import com.openwallet.ledger.dto.WithdrawalRequest;
import com.openwallet.ledger.service.TransactionService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDateTime;

@RestController
@RequestMapping("/api/v1/transactions")
@RequiredArgsConstructor
public class TransactionController {

    private final TransactionService transactionService;

    @PostMapping("/deposits")
    @PreAuthorize("hasRole('USER')")
    public ResponseEntity<TransactionResponse> createDeposit(@Valid @RequestBody DepositRequest request) {
        TransactionResponse response = transactionService.createDeposit(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @PostMapping("/withdrawals")
    @PreAuthorize("hasRole('USER')")
    public ResponseEntity<TransactionResponse> createWithdrawal(@Valid @RequestBody WithdrawalRequest request) {
        TransactionResponse response = transactionService.createWithdrawal(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @PostMapping("/transfers")
    @PreAuthorize("hasRole('USER')")
    public ResponseEntity<TransactionResponse> createTransfer(@Valid @RequestBody TransferRequest request) {
        TransactionResponse response = transactionService.createTransfer(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAnyRole('USER', 'ADMIN', 'AUDITOR')")
    public ResponseEntity<TransactionResponse> getTransaction(@PathVariable("id") Long id) {
        TransactionResponse response = transactionService.getTransaction(id);
        return ResponseEntity.ok(response);
    }

    @GetMapping
    @PreAuthorize("hasAnyRole('USER', 'ADMIN', 'AUDITOR')")
    public ResponseEntity<TransactionListResponse> getTransactions(
            @RequestParam(required = false) Long walletId,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime fromDate,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime toDate,
            @RequestParam(required = false) TransactionStatus status,
            @RequestParam(required = false) TransactionType transactionType,
            @RequestParam(required = false, defaultValue = "0") Integer page,
            @RequestParam(required = false, defaultValue = "20") Integer size,
            @RequestParam(required = false) String sortBy,
            @RequestParam(required = false, defaultValue = "desc") String sortDirection
    ) {
        TransactionListResponse response = transactionService.getTransactions(
                walletId,
                fromDate,
                toDate,
                status,
                transactionType,
                page,
                size,
                sortBy,
                sortDirection
        );
        return ResponseEntity.ok(response);
    }
}
