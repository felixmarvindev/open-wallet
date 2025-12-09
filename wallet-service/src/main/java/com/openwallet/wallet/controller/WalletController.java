package com.openwallet.wallet.controller;

import com.openwallet.wallet.dto.CreateWalletRequest;
import com.openwallet.wallet.dto.WalletResponse;
import com.openwallet.wallet.service.WalletService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/v1/wallets")
@RequiredArgsConstructor
public class WalletController {

    private final WalletService walletService;

    @PostMapping
    @PreAuthorize("hasRole('USER')")
    public ResponseEntity<WalletResponse> createWallet(
            @RequestHeader("X-Customer-Id") Long customerId,
            @Valid @RequestBody CreateWalletRequest request
    ) {
        WalletResponse response = walletService.createWallet(customerId, request);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasRole('USER')")
    public ResponseEntity<WalletResponse> getWallet(
            @PathVariable("id") Long walletId,
            @RequestHeader("X-Customer-Id") Long customerId
    ) {
        WalletResponse response = walletService.getWallet(walletId, customerId);
        return ResponseEntity.ok(response);
    }

    @GetMapping("/{id}/balance")
    @PreAuthorize("hasRole('USER')")
    public ResponseEntity<com.openwallet.wallet.dto.BalanceResponse> getWalletBalance(
            @PathVariable("id") Long walletId,
            @RequestHeader("X-Customer-Id") Long customerId) {
        return ResponseEntity.ok(walletService.getWalletBalance(walletId, customerId));
    }

    @GetMapping("/me")
    @PreAuthorize("hasRole('USER')")
    public ResponseEntity<List<WalletResponse>> getMyWallets(
            @RequestHeader("X-Customer-Id") Long customerId
    ) {
        List<WalletResponse> wallets = walletService.getMyWallets(customerId);
        return ResponseEntity.ok(wallets);
    }
}


