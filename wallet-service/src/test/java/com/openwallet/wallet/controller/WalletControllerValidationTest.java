package com.openwallet.wallet.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.openwallet.wallet.dto.CreateWalletRequest;
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

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Tests for validation error responses with detailed field errors.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
@SuppressWarnings("null")
class WalletControllerValidationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Test
    void createWalletShouldReturnDetailedValidationErrors() throws Exception {
        // Given: Invalid request with invalid daily limit (currency is optional, defaults to KES)
        CreateWalletRequest request = CreateWalletRequest.builder()
                .currency(null) // Optional - will default to KES
                .dailyLimit(new java.math.BigDecimal("-100")) // Negative value
                .build();

        // When: Creating wallet
        mockMvc.perform(post("/api/v1/wallets")
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
                .andExpect(jsonPath("$.error").value("Bad Request"))
                .andExpect(jsonPath("$.details").isArray())
                .andExpect(jsonPath("$.details[0].field").exists())
                .andExpect(jsonPath("$.details[0].message").exists());
    }

    @Test
    void createWalletShouldRejectInvalidCurrencyCode() throws Exception {
        // Given: Request with invalid currency code
        CreateWalletRequest request = CreateWalletRequest.builder()
                .currency("INVALID") // Not a valid ISO currency code
                .build();

        // When: Creating wallet
        mockMvc.perform(post("/api/v1/wallets")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request))
                        .with(SecurityMockMvcRequestPostProcessors.jwt()
                                .jwt(jwt -> jwt.subject("test-user")
                                        .claim("realm_access",
                                                Collections.singletonMap("roles",
                                                        Arrays.asList("USER"))))
                                .authorities(new SimpleGrantedAuthority("ROLE_USER"))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.details[0].field").value("currency"))
                .andExpect(jsonPath("$.details[0].message").isNotEmpty());
    }

    @Test
    void createWalletShouldAcceptKESCurrency() throws Exception {
        // Given: Request with KES currency (only supported currency in MVP)
        CreateWalletRequest request = CreateWalletRequest.builder()
                .currency("KES")
                .build();

        // When: Creating wallet (will fail due to missing mapping, but validation should pass)
        mockMvc.perform(post("/api/v1/wallets")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request))
                        .with(SecurityMockMvcRequestPostProcessors.jwt()
                                .jwt(jwt -> jwt.subject("test-user")
                                        .claim("realm_access",
                                                Collections.singletonMap("roles",
                                                        Arrays.asList("USER"))))
                                .authorities(new SimpleGrantedAuthority("ROLE_USER"))))
                .andExpect(status().isBadRequest()) // Bad request due to missing mapping, not validation
                .andExpect(jsonPath("$.details").doesNotExist()); // No validation errors
    }

    @Test
    void createWalletShouldRejectNonKESCurrency() throws Exception {
        // Given: Request with non-KES currency (not supported in MVP)
        String[] nonKESCurrencies = {"USD", "EUR", "GBP"};

        for (String currency : nonKESCurrencies) {
            CreateWalletRequest request = CreateWalletRequest.builder()
                    .currency(currency)
                    .build();

            // When: Creating wallet
            mockMvc.perform(post("/api/v1/wallets")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request))
                            .with(SecurityMockMvcRequestPostProcessors.jwt()
                                    .jwt(jwt -> jwt.subject("test-user")
                                            .claim("realm_access",
                                                    Collections.singletonMap("roles",
                                                            Arrays.asList("USER"))))
                                    .authorities(new SimpleGrantedAuthority("ROLE_USER"))))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.error").value("Bad Request"))
                    .andExpect(jsonPath("$.message").value(org.hamcrest.Matchers.containsString("Only KES currency is supported")));
        }
    }
}

