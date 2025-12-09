package com.openwallet.customer.controller;

import com.openwallet.customer.dto.CustomerResponse;
import com.openwallet.customer.dto.UpdateCustomerRequest;
import com.openwallet.customer.service.CustomerService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
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

    @GetMapping("/me")
    public ResponseEntity<CustomerResponse> getMyProfile(
            @RequestHeader("X-User-Id") String userId
    ) {
        return ResponseEntity.ok(customerService.getCurrentCustomer(userId));
    }

    @PutMapping("/me")
    public ResponseEntity<CustomerResponse> updateMyProfile(
            @RequestHeader("X-User-Id") String userId,
            @Valid @RequestBody UpdateCustomerRequest request
    ) {
        return ResponseEntity.ok(customerService.updateCurrentCustomer(userId, request));
    }
}


