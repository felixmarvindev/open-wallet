package com.openwallet.customer.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.openwallet.customer.dto.CreateCustomerRequest;
import com.openwallet.customer.dto.KycInitiateRequest;
import com.openwallet.customer.dto.KycWebhookRequest;
import com.openwallet.customer.dto.UpdateCustomerRequest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Tests for validation error responses in customer endpoints.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
@SuppressWarnings("null")
class CustomerControllerValidationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Test
    void createCustomerShouldReturnDetailedValidationErrors() throws Exception {
        // Given: Invalid create request
        CreateCustomerRequest request = CreateCustomerRequest.builder()
                .firstName("") // Blank
                .lastName("") // Blank
                .phoneNumber("0123456789") // Invalid format (starts with 0, pattern requires first digit to be 1-9)
                .email("not-an-email") // Invalid email
                .address(new String(new char[501]).replace('\0', 'A')) // Too long (501 chars)
                .build();

        // When: Creating customer
        mockMvc.perform(post("/api/v1/customers")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request))
                        .with(SecurityMockMvcRequestPostProcessors.jwt()
                                .jwt(jwt -> jwt.subject("test-user")
                                        .claim("realm_access",
                                                Collections.singletonMap("roles",
                                                        Arrays.asList("USER"))))
                                .authorities(new SimpleGrantedAuthority("ROLE_USER"))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.details").isArray())
                .andExpect(jsonPath("$.details.length()").value(org.hamcrest.Matchers.greaterThan(0)))
                .andExpect(jsonPath("$.details[?(@.field == 'firstName')]").exists())
                .andExpect(jsonPath("$.details[?(@.field == 'lastName')]").exists())
                .andExpect(jsonPath("$.details[?(@.field == 'phoneNumber')]").exists())
                .andExpect(jsonPath("$.details[?(@.field == 'email')]").exists());
    }

    @Test
    void updateCustomerShouldReturnDetailedValidationErrors() throws Exception {
        // Given: Invalid update request
        UpdateCustomerRequest request = UpdateCustomerRequest.builder()
                .firstName("") // Blank
                .lastName("") // Blank
                .phoneNumber("123") // Invalid format
                .email("not-an-email") // Invalid email
                .build();

        // When: Updating customer
        mockMvc.perform(put("/api/v1/customers/me")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request))
                        .with(SecurityMockMvcRequestPostProcessors.jwt()
                                .jwt(jwt -> jwt.subject("test-user")
                                        .claim("realm_access",
                                                Collections.singletonMap("roles",
                                                        Arrays.asList("USER"))))
                                .authorities(new SimpleGrantedAuthority("ROLE_USER"))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.details").isArray())
                .andExpect(jsonPath("$.details.length()").value(org.hamcrest.Matchers.greaterThan(0)));
    }

    @Test
    void initiateKycShouldRejectEmptyDocuments() throws Exception {
        // Given: KYC request with empty documents
        Map<String, Object> emptyDocuments = new HashMap<>();
        KycInitiateRequest request = KycInitiateRequest.builder()
                .documents(emptyDocuments) // Empty map
                .build();

        // When: Initiating KYC
        mockMvc.perform(post("/api/v1/customers/me/kyc/initiate")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request))
                        .with(SecurityMockMvcRequestPostProcessors.jwt()
                                .jwt(jwt -> jwt.subject("test-user")
                                        .claim("realm_access",
                                                Collections.singletonMap("roles",
                                                        Arrays.asList("USER"))))
                                .authorities(new SimpleGrantedAuthority("ROLE_USER"))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.details[?(@.field == 'documents')]").exists())
                .andExpect(jsonPath("$.details[?(@.field == 'documents')].message").isNotEmpty());
    }

    @Test
    void kycWebhookShouldRejectInvalidStatus() throws Exception {
        // Given: Webhook request with invalid status
        KycWebhookRequest request = KycWebhookRequest.builder()
                .providerReference("ref-123")
                .status("INVALID_STATUS") // Invalid status
                .customerId(1L)
                .build();

        // When: Processing webhook (public endpoint, no auth needed)
        mockMvc.perform(post("/api/v1/customers/kyc/webhook")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.details[?(@.field == 'status')]").exists())
                .andExpect(jsonPath("$.details[?(@.field == 'status')].message").isNotEmpty());
    }

    @Test
    void kycWebhookShouldAcceptValidStatus() throws Exception {
        // Given: Webhook request with valid status
        KycWebhookRequest request = KycWebhookRequest.builder()
                .providerReference("ref-123")
                .status("VERIFIED") // Valid status
                .customerId(1L)
                .build();

        // When: Processing webhook (will fail due to missing customer, but validation should pass)
        mockMvc.perform(post("/api/v1/customers/kyc/webhook")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isNotFound()) // Not found due to missing customer, not validation
                .andExpect(jsonPath("$.details").doesNotExist()); // No validation errors
    }
}

