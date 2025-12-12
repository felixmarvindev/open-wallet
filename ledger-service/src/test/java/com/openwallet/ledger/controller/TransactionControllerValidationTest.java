package com.openwallet.ledger.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.openwallet.ledger.dto.DepositRequest;
import com.openwallet.ledger.dto.TransferRequest;
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

import java.math.BigDecimal;
import java.util.Arrays;
import java.util.Collections;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Tests for validation error responses in transaction endpoints.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
@SuppressWarnings("null")
class TransactionControllerValidationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Test
    void createDepositShouldReturnDetailedValidationErrors() throws Exception {
        // Given: Invalid deposit request
        DepositRequest request = DepositRequest.builder()
                .toWalletId(null) // Missing required
                .amount(new BigDecimal("-10")) // Negative amount
                .currency("XX") // Invalid currency code
                .idempotencyKey("") // Blank
                .build();

        // When: Creating deposit
        mockMvc.perform(post("/api/v1/transactions/deposits")
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
    void createTransferShouldRejectInvalidCurrencyCode() throws Exception {
        // Given: Transfer request with invalid currency
        TransferRequest request = TransferRequest.builder()
                .fromWalletId(1L)
                .toWalletId(2L)
                .amount(new BigDecimal("100"))
                .currency("INVALID") // Invalid currency code
                .idempotencyKey("key-123")
                .build();

        // When: Creating transfer
        mockMvc.perform(post("/api/v1/transactions/transfers")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request))
                        .with(SecurityMockMvcRequestPostProcessors.jwt()
                                .jwt(jwt -> jwt.subject("test-user")
                                        .claim("realm_access",
                                                Collections.singletonMap("roles",
                                                        Arrays.asList("USER"))))
                                .authorities(new SimpleGrantedAuthority("ROLE_USER"))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.details[?(@.field == 'currency')]").exists())
                .andExpect(jsonPath("$.details[?(@.field == 'currency')].message").isNotEmpty());
    }

    @Test
    void createDepositShouldRejectNegativeAmount() throws Exception {
        // Given: Deposit with negative amount
        DepositRequest request = DepositRequest.builder()
                .toWalletId(1L)
                .amount(new BigDecimal("-50")) // Negative amount
                .currency("KES")
                .idempotencyKey("key-123")
                .build();

        // When: Creating deposit
        mockMvc.perform(post("/api/v1/transactions/deposits")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request))
                        .with(SecurityMockMvcRequestPostProcessors.jwt()
                                .jwt(jwt -> jwt.subject("test-user")
                                        .claim("realm_access",
                                                Collections.singletonMap("roles",
                                                        Arrays.asList("USER"))))
                                .authorities(new SimpleGrantedAuthority("ROLE_USER"))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.details[?(@.field == 'amount')]").exists())
                .andExpect(jsonPath("$.details[?(@.field == 'amount')].message").isNotEmpty());
    }
}

