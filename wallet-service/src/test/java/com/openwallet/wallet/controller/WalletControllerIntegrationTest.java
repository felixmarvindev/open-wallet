package com.openwallet.wallet.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.openwallet.wallet.domain.CustomerUserMapping;
import com.openwallet.wallet.domain.Wallet;
import com.openwallet.wallet.domain.WalletStatus;
import com.openwallet.wallet.dto.CreateWalletRequest;
import com.openwallet.wallet.repository.CustomerUserMappingRepository;
import com.openwallet.wallet.repository.WalletRepository;
import org.junit.jupiter.api.BeforeEach;
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

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Integration test for WalletController with JWT authentication and customerId resolution.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
@SuppressWarnings("null")
class WalletControllerIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private WalletRepository walletRepository;

    @Autowired
    private CustomerUserMappingRepository mappingRepository;

    @BeforeEach
    void clean() {
        walletRepository.deleteAll();
        mappingRepository.deleteAll();
    }

    @Test
    void createWalletShouldResolveCustomerIdFromJWT() throws Exception {
        // Given: Mapping exists for userId
        CustomerUserMapping mapping = mappingRepository.save(CustomerUserMapping.builder()
                .userId("wallet-user-1")
                .customerId(100L)
                .build());

        CreateWalletRequest request = CreateWalletRequest.builder()
                .currency("KES")
                .build();

        // When: Creating wallet with JWT (no header needed)
        mockMvc.perform(post("/api/v1/wallets")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request))
                        .with(SecurityMockMvcRequestPostProcessors.jwt()
                                .jwt(jwt -> jwt.subject("wallet-user-1")
                                        .claim("realm_access",
                                                Collections.singletonMap("roles",
                                                        Arrays.asList("USER"))))
                                .authorities(new SimpleGrantedAuthority("ROLE_USER"))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.customerId").value(100L))
                .andExpect(jsonPath("$.currency").value("KES"));

        // Then: Wallet should be created with correct customerId
        assertThat(walletRepository.findByCustomerId(100L)).hasSize(1);
    }

    @Test
    void createWalletShouldValidateHeaderMatchesJWT() throws Exception {
        // Given: Mapping exists
        CustomerUserMapping mapping = mappingRepository.save(CustomerUserMapping.builder()
                .userId("wallet-user-2")
                .customerId(200L)
                .build());

        CreateWalletRequest request = CreateWalletRequest.builder()
                .currency("KES")
                .build();

        // When: Creating wallet with mismatched header
        mockMvc.perform(post("/api/v1/wallets")
                        .header("X-Customer-Id", "999") // Wrong customerId
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request))
                        .with(SecurityMockMvcRequestPostProcessors.jwt()
                                .jwt(jwt -> jwt.subject("wallet-user-2")
                                        .claim("realm_access",
                                                Collections.singletonMap("roles",
                                                        Arrays.asList("USER"))))
                                .authorities(new SimpleGrantedAuthority("ROLE_USER"))))
                .andExpect(status().isForbidden());
    }

    @Test
    void getWalletShouldResolveCustomerIdFromJWT() throws Exception {
        // Given: Mapping and wallet exist
        CustomerUserMapping mapping = mappingRepository.save(CustomerUserMapping.builder()
                .userId("wallet-user-3")
                .customerId(300L)
                .build());

        Wallet wallet = walletRepository.save(Wallet.builder()
                .customerId(300L)
                .currency("USD")
                .status(WalletStatus.ACTIVE)
                .balance(new BigDecimal("100.00"))
                .build());

        // When: Getting wallet with JWT
        mockMvc.perform(get("/api/v1/wallets/" + wallet.getId())
                        .with(SecurityMockMvcRequestPostProcessors.jwt()
                                .jwt(jwt -> jwt.subject("wallet-user-3")
                                        .claim("realm_access",
                                                Collections.singletonMap("roles",
                                                        Arrays.asList("USER"))))
                                .authorities(new SimpleGrantedAuthority("ROLE_USER"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(wallet.getId()))
                .andExpect(jsonPath("$.customerId").value(300L))
                .andExpect(jsonPath("$.currency").value("USD"));
    }

    @Test
    void getMyWalletsShouldResolveCustomerIdFromJWT() throws Exception {
        // Given: Mapping and wallets exist
        CustomerUserMapping mapping = mappingRepository.save(CustomerUserMapping.builder()
                .userId("wallet-user-4")
                .customerId(400L)
                .build());

        walletRepository.save(Wallet.builder()
                .customerId(400L)
                .currency("KES")
                .status(WalletStatus.ACTIVE)
                .build());

        walletRepository.save(Wallet.builder()
                .customerId(400L)
                .currency("USD")
                .status(WalletStatus.ACTIVE)
                .build());

        // When: Getting my wallets with JWT
        mockMvc.perform(get("/api/v1/wallets/me")
                        .with(SecurityMockMvcRequestPostProcessors.jwt()
                                .jwt(jwt -> jwt.subject("wallet-user-4")
                                        .claim("realm_access",
                                                Collections.singletonMap("roles",
                                                        Arrays.asList("USER"))))
                                .authorities(new SimpleGrantedAuthority("ROLE_USER"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isArray())
                .andExpect(jsonPath("$.length()").value(2));
    }

    @Test
    void createWalletShouldFailWhenMappingNotFound() throws Exception {
        // Given: No mapping exists for userId
        CreateWalletRequest request = CreateWalletRequest.builder()
                .currency("KES")
                .build();

        // When: Creating wallet with JWT for non-existent mapping
        mockMvc.perform(post("/api/v1/wallets")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request))
                        .with(SecurityMockMvcRequestPostProcessors.jwt()
                                .jwt(jwt -> jwt.subject("non-existent-user")
                                        .claim("realm_access",
                                                Collections.singletonMap("roles",
                                                        Arrays.asList("USER"))))
                                .authorities(new SimpleGrantedAuthority("ROLE_USER"))))
                .andExpect(status().isBadRequest());
    }
}

