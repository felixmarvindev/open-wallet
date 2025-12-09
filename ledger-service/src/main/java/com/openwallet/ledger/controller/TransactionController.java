package com.openwallet.ledger.controller;

import com.openwallet.ledger.dto.DepositRequest;
import com.openwallet.ledger.dto.TransactionResponse;
import com.openwallet.ledger.dto.TransferRequest;
import com.openwallet.ledger.dto.WithdrawalRequest;
import com.openwallet.ledger.service.TransactionService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

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
}
