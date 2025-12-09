package com.openwallet.customer.controller;

import com.openwallet.customer.dto.CustomerResponse;
import com.openwallet.customer.dto.KycInitiateRequest;
import com.openwallet.customer.dto.KycStatusResponse;
import com.openwallet.customer.dto.KycWebhookRequest;
import com.openwallet.customer.dto.UpdateCustomerRequest;
import com.openwallet.customer.service.CustomerService;
import com.openwallet.customer.service.KycService;
import com.openwallet.customer.config.JwtUtils;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Customer-facing endpoints for viewing and updating the authenticated user's profile.
 */
@RestController
@RequestMapping("/api/v1/customers")
@RequiredArgsConstructor
public class CustomerController {

    private final CustomerService customerService;
    private final KycService kycService;

    @GetMapping("/me")
    @PreAuthorize("hasRole('USER')")
    public ResponseEntity<CustomerResponse> getMyProfile(
            @RequestHeader(value = "X-User-Id", required = false) String headerUserId) {
        String userId = resolveUserId(headerUserId);
        return ResponseEntity.ok(customerService.getCurrentCustomer(userId));
    }

    @PutMapping("/me")
    @PreAuthorize("hasRole('USER')")
    public ResponseEntity<CustomerResponse> updateMyProfile(
            @RequestHeader(value = "X-User-Id", required = false) String headerUserId,
            @Valid @RequestBody UpdateCustomerRequest request
    ) {
        String userId = resolveUserId(headerUserId);
        return ResponseEntity.ok(customerService.updateCurrentCustomer(userId, request));
    }

    @PostMapping("/me/kyc/initiate")
    @PreAuthorize("hasRole('USER')")
    public ResponseEntity<KycStatusResponse> initiateKyc(
            @RequestHeader(value = "X-User-Id", required = false) String headerUserId,
            @Valid @RequestBody KycInitiateRequest request
    ) {
        String userId = resolveUserId(headerUserId);
        return ResponseEntity.ok(kycService.initiateKyc(userId, request));
    }

    @GetMapping("/me/kyc/status")
    @PreAuthorize("hasRole('USER')")
    public ResponseEntity<KycStatusResponse> getKycStatus(
            @RequestHeader(value = "X-User-Id", required = false) String headerUserId) {
        String userId = resolveUserId(headerUserId);
        return ResponseEntity.ok(kycService.getKycStatus(userId));
    }

    @PostMapping("/kyc/webhook")
    // Webhook is public (external provider callback)
    public ResponseEntity<KycStatusResponse> handleWebhook(
            @Valid @RequestBody KycWebhookRequest request
    ) {
        return ResponseEntity.ok(kycService.handleWebhook(request));
    }

    /**
     * Resolve userId from JWT and optionally validate against provided header.
     * Header is kept for backward compatibility with existing tests.
     */
    private String resolveUserId(String headerUserId) {
        String jwtUserId = JwtUtils.getUserId();
        if (jwtUserId == null) {
            throw new IllegalStateException("User ID not found in JWT token");
        }
        if (headerUserId != null && !headerUserId.equals(jwtUserId)) {
            throw new AccessDeniedException("User ID header does not match authenticated user");
        }
        return jwtUserId;
    }
}


