package com.openwallet.wallet.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.openwallet.wallet.dto.CreateWalletRequest;
import com.openwallet.wallet.dto.WalletResponse;
import com.openwallet.wallet.service.WalletService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(controllers = WalletController.class)
@AutoConfigureMockMvc(addFilters = false)
@ActiveProfiles("test")
@SuppressWarnings({ "DataFlowIssue", "null" })
class WalletControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private WalletService walletService;

    @Test
    @DisplayName("POST /api/v1/wallets returns 201 with body")
    void createWalletShouldReturnCreated() throws Exception {
        CreateWalletRequest request = CreateWalletRequest.builder()
                .currency("KES")
                .dailyLimit(new BigDecimal("1000.00"))
                .monthlyLimit(new BigDecimal("5000.00"))
                .build();

        WalletResponse response = sampleResponse(1L, "KES");
        Mockito.when(walletService.createWallet(eq(10L), any(CreateWalletRequest.class)))
                .thenReturn(response);

        mockMvc.perform(post("/api/v1/wallets")
                .header("X-Customer-Id", "10")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(content().json(objectMapper.writeValueAsString(response)));
    }

    @Test
    @DisplayName("POST /api/v1/wallets fails with 400 when currency missing")
    void createWalletShouldFailValidation() throws Exception {
        String payload = "{\"currency\":\"\"}";

        mockMvc.perform(post("/api/v1/wallets")
                .header("X-Customer-Id", "10")
                .contentType(MediaType.APPLICATION_JSON)
                .content(payload))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("GET /api/v1/wallets/{id} returns 200 when owned")
    void getWalletShouldReturnOk() throws Exception {
        WalletResponse response = sampleResponse(5L, "USD");
        Mockito.when(walletService.getWallet(5L, 10L)).thenReturn(response);

        mockMvc.perform(get("/api/v1/wallets/5")
                .header("X-Customer-Id", "10"))
                .andExpect(status().isOk())
                .andExpect(content().json(objectMapper.writeValueAsString(response)));
    }

    @Test
    @DisplayName("GET /api/v1/wallets/me returns list")
    void getMyWalletsShouldReturnList() throws Exception {
        List<WalletResponse> responses = Arrays.asList(
                sampleResponse(1L, "KES"),
                sampleResponse(2L, "USD"));
        Mockito.when(walletService.getMyWallets(10L)).thenReturn(responses);

        mockMvc.perform(get("/api/v1/wallets/me")
                .header("X-Customer-Id", "10"))
                .andExpect(status().isOk())
                .andExpect(content().json(objectMapper.writeValueAsString(responses)));
    }

    private WalletResponse sampleResponse(Long id, String currency) {
        return WalletResponse.builder()
                .id(id)
                .customerId(10L)
                .currency(currency)
                .status("ACTIVE")
                .balance(BigDecimal.ZERO)
                .dailyLimit(new BigDecimal("100000.00"))
                .monthlyLimit(new BigDecimal("1000000.00"))
                .createdAt(LocalDateTime.now())
                .updatedAt(LocalDateTime.now())
                .build();
    }
}
