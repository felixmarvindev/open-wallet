package com.openwallet.wallet.controller;

import com.openwallet.wallet.config.JwtUtils;
import com.openwallet.wallet.dto.CreateWalletRequest;
import com.openwallet.wallet.dto.WalletResponse;
import com.openwallet.wallet.service.CustomerIdResolver;
import com.openwallet.wallet.service.WalletService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
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
    private final CustomerIdResolver customerIdResolver;

    @PostMapping
    @PreAuthorize("hasRole('USER')")
    public ResponseEntity<WalletResponse> createWallet(
            @RequestHeader(value = "X-Customer-Id", required = false) Long headerCustomerId,
            @Valid @RequestBody CreateWalletRequest request
    ) {
        Long customerId = resolveCustomerId(headerCustomerId);
        WalletResponse response = walletService.createWallet(customerId, request);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasRole('USER')")
    public ResponseEntity<WalletResponse> getWallet(
            @PathVariable("id") Long walletId,
            @RequestHeader(value = "X-Customer-Id", required = false) Long headerCustomerId
    ) {
        Long customerId = resolveCustomerId(headerCustomerId);
        WalletResponse response = walletService.getWallet(walletId, customerId);
        return ResponseEntity.ok(response);
    }

    @GetMapping("/{id}/balance")
    @PreAuthorize("hasRole('USER')")
    public ResponseEntity<com.openwallet.wallet.dto.BalanceResponse> getWalletBalance(
            @PathVariable("id") Long walletId,
            @RequestHeader(value = "X-Customer-Id", required = false) Long headerCustomerId) {
        Long customerId = resolveCustomerId(headerCustomerId);
        return ResponseEntity.ok(walletService.getWalletBalance(walletId, customerId));
    }

    @GetMapping("/me")
    @PreAuthorize("hasRole('USER')")
    public ResponseEntity<List<WalletResponse>> getMyWallets(
            @RequestHeader(value = "X-Customer-Id", required = false) Long headerCustomerId
    ) {
        Long customerId = resolveCustomerId(headerCustomerId);
        List<WalletResponse> wallets = walletService.getMyWallets(customerId);
        return ResponseEntity.ok(wallets);
    }

    /**
     * Resolve customerId from JWT and optionally validate against provided header.
     * Header is kept for backward compatibility with existing tests.
     */
    private Long resolveCustomerId(Long headerCustomerId) {
        String userId = JwtUtils.getUserId();
        if (userId == null) {
            throw new IllegalStateException("User ID not found in JWT token");
        }

        Long customerId = customerIdResolver.resolveCustomerId(userId);

        // Validate header matches if provided
        if (headerCustomerId != null && !headerCustomerId.equals(customerId)) {
            throw new AccessDeniedException("Customer ID header does not match authenticated user");
        }

        return customerId;
    }
}


