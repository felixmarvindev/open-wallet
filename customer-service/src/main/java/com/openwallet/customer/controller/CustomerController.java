package com.openwallet.customer.controller;

import com.openwallet.customer.config.JwtUtils;
import com.openwallet.customer.dto.CustomerResponse;
import com.openwallet.customer.dto.KycInitiateRequest;
import com.openwallet.customer.dto.KycStatusResponse;
import com.openwallet.customer.dto.KycWebhookRequest;
import com.openwallet.customer.dto.UpdateCustomerRequest;
import com.openwallet.customer.service.CustomerService;
import com.openwallet.customer.service.KycService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
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
    public ResponseEntity<CustomerResponse> getMyProfile() {
        String userId = JwtUtils.getUserId();
        if (userId == null) {
            throw new IllegalStateException("User ID not found in JWT token");
        }
        return ResponseEntity.ok(customerService.getCurrentCustomer(userId));
    }

    @PutMapping("/me")
    @PreAuthorize("hasRole('USER')")
    public ResponseEntity<CustomerResponse> updateMyProfile(
            @Valid @RequestBody UpdateCustomerRequest request
    ) {
        String userId = JwtUtils.getUserId();
        if (userId == null) {
            throw new IllegalStateException("User ID not found in JWT token");
        }
        return ResponseEntity.ok(customerService.updateCurrentCustomer(userId, request));
    }

    @PostMapping("/me/kyc/initiate")
    @PreAuthorize("hasRole('USER')")
    public ResponseEntity<KycStatusResponse> initiateKyc(
            @Valid @RequestBody KycInitiateRequest request
    ) {
        String userId = JwtUtils.getUserId();
        if (userId == null) {
            throw new IllegalStateException("User ID not found in JWT token");
        }
        return ResponseEntity.ok(kycService.initiateKyc(userId, request));
    }

    @GetMapping("/me/kyc/status")
    @PreAuthorize("hasRole('USER')")
    public ResponseEntity<KycStatusResponse> getKycStatus() {
        String userId = JwtUtils.getUserId();
        if (userId == null) {
            throw new IllegalStateException("User ID not found in JWT token");
        }
        return ResponseEntity.ok(kycService.getKycStatus(userId));
    }

    @PostMapping("/kyc/webhook")
    // Webhook is public (external provider callback)
    public ResponseEntity<KycStatusResponse> handleWebhook(
            @Valid @RequestBody KycWebhookRequest request
    ) {
        return ResponseEntity.ok(kycService.handleWebhook(request));
    }
}


