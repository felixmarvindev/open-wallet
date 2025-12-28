package com.openwallet.wallet.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.openwallet.wallet.config.JwtUtils;
import com.openwallet.wallet.dto.CreateWalletRequest;
import com.openwallet.wallet.dto.WalletResponse;
import com.openwallet.wallet.service.BalanceReconciliationService;
import com.openwallet.wallet.service.CustomerIdResolver;
import com.openwallet.wallet.service.WalletService;
import org.mockito.MockedStatic;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;
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
@SuppressWarnings({"null" })
class WalletControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private WalletService walletService;

    @MockBean
    private CustomerIdResolver customerIdResolver;

    @MockBean
    private BalanceReconciliationService balanceReconciliationService;

    @org.junit.jupiter.api.BeforeEach
    void setUp() {
        // Mock CustomerIdResolver to return customerId when called
        Mockito.when(customerIdResolver.resolveCustomerId(Mockito.anyString()))
                .thenReturn(10L); // Default customer ID for tests
    }

    @Test
    @DisplayName("POST /api/v1/wallets returns 201 with body")
    void createWalletShouldReturnCreated() throws Exception {
        try (MockedStatic<JwtUtils> jwtUtilsMock = Mockito.mockStatic(JwtUtils.class)) {
            jwtUtilsMock.when(JwtUtils::getUserId).thenReturn("test-user-id");

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
        try (MockedStatic<JwtUtils> jwtUtilsMock = Mockito.mockStatic(JwtUtils.class)) {
            jwtUtilsMock.when(JwtUtils::getUserId).thenReturn("test-user-id");

            WalletResponse response = sampleResponse(5L, "KES");
            Mockito.when(walletService.getWallet(5L, 10L)).thenReturn(response);

            mockMvc.perform(get("/api/v1/wallets/5")
                    .header("X-Customer-Id", "10"))
                    .andExpect(status().isOk())
                    .andExpect(content().json(objectMapper.writeValueAsString(response)));
        }
    }

    @Test
    @DisplayName("GET /api/v1/wallets/{id}/balance returns 200 when owned")
    void getWalletBalanceShouldReturnOk() throws Exception {
        try (MockedStatic<JwtUtils> jwtUtilsMock = Mockito.mockStatic(JwtUtils.class)) {
            jwtUtilsMock.when(JwtUtils::getUserId).thenReturn("test-user-id");

            com.openwallet.wallet.dto.BalanceResponse balance = com.openwallet.wallet.dto.BalanceResponse.builder()
                    .balance(new BigDecimal("10.00"))
                    .currency("KES")
                    .lastUpdated("2024-01-01T00:00:00")
                    .build();
            Mockito.when(walletService.getWalletBalance(5L, 10L)).thenReturn(balance);

            mockMvc.perform(get("/api/v1/wallets/5/balance")
                            .header("X-Customer-Id", "10"))
                    .andExpect(status().isOk())
                    .andExpect(content().json(objectMapper.writeValueAsString(balance)));
        }
    }

    @Test
    @DisplayName("GET /api/v1/wallets/me returns list")
    void getMyWalletsShouldReturnList() throws Exception {
        try (MockedStatic<JwtUtils> jwtUtilsMock = Mockito.mockStatic(JwtUtils.class)) {
            jwtUtilsMock.when(JwtUtils::getUserId).thenReturn("test-user-id");

            // For MVP, only KES is supported, so customers typically have one wallet
            List<WalletResponse> responses = Arrays.asList(
                    sampleResponse(1L, "KES"));
            Mockito.when(walletService.getMyWallets(10L)).thenReturn(responses);

            mockMvc.perform(get("/api/v1/wallets/me")
                    .header("X-Customer-Id", "10"))
                    .andExpect(status().isOk())
                    .andExpect(content().json(objectMapper.writeValueAsString(responses)));
        }
    }

    @Test
    @DisplayName("GET /api/v1/wallets/{id}/transactions returns 200 with paginated results")
    void getWalletTransactionsShouldReturnOk() throws Exception {
        try (MockedStatic<JwtUtils> jwtUtilsMock = Mockito.mockStatic(JwtUtils.class)) {
            jwtUtilsMock.when(JwtUtils::getUserId).thenReturn("test-user-id");

            com.openwallet.wallet.dto.TransactionListResponse response = com.openwallet.wallet.dto.TransactionListResponse.builder()
                    .transactions(List.of(
                            sampleTransactionItem(1L, "DEPOSIT"),
                            sampleTransactionItem(2L, "WITHDRAWAL")
                    ))
                    .pagination(com.openwallet.wallet.dto.TransactionListResponse.PaginationMetadata.builder()
                            .page(0)
                            .size(20)
                            .totalElements(2)
                            .totalPages(1)
                            .hasNext(false)
                            .hasPrevious(false)
                            .build())
                    .build();

            Mockito.when(walletService.getWalletTransactions(
                    eq(5L), eq(10L), eq(null), eq(null), eq(null), eq(null),
                    eq(0), eq(20), eq(null), eq("desc")
            )).thenReturn(response);

            mockMvc.perform(get("/api/v1/wallets/5/transactions")
                    .header("X-Customer-Id", "10")
                    .param("page", "0")
                    .param("size", "20"))
                    .andExpect(status().isOk())
                    .andExpect(content().json(objectMapper.writeValueAsString(response)));
        }
    }

    @Test
    @DisplayName("GET /api/v1/wallets/{id}/transactions with filters returns filtered results")
    void getWalletTransactionsWithFiltersShouldReturnFilteredResults() throws Exception {
        try (MockedStatic<JwtUtils> jwtUtilsMock = Mockito.mockStatic(JwtUtils.class)) {
            jwtUtilsMock.when(JwtUtils::getUserId).thenReturn("test-user-id");
        com.openwallet.wallet.dto.TransactionListResponse response = com.openwallet.wallet.dto.TransactionListResponse.builder()
                .transactions(List.of(sampleTransactionItem(1L, "DEPOSIT")))
                .pagination(com.openwallet.wallet.dto.TransactionListResponse.PaginationMetadata.builder()
                        .page(0)
                        .size(20)
                        .totalElements(1)
                        .totalPages(1)
                        .hasNext(false)
                        .hasPrevious(false)
                        .build())
                .build();

        Mockito.when(walletService.getWalletTransactions(
                eq(5L), eq(10L), eq(null), eq(null), eq("COMPLETED"), eq("DEPOSIT"),
                eq(0), eq(20), eq(null), eq("desc")
        )).thenReturn(response);

        mockMvc.perform(get("/api/v1/wallets/5/transactions")
                .header("X-Customer-Id", "10")
                .param("status", "COMPLETED")
                .param("transactionType", "DEPOSIT")
                .param("page", "0")
                .param("size", "20"))
                .andExpect(status().isOk())
                .andExpect(content().json(objectMapper.writeValueAsString(response)));
        }
    }

    @Test
    @DisplayName("GET /api/v1/wallets/{id}/transactions with date range returns filtered results")
    void getWalletTransactionsWithDateRangeShouldReturnFilteredResults() throws Exception {
        try (MockedStatic<JwtUtils> jwtUtilsMock = Mockito.mockStatic(JwtUtils.class)) {
            jwtUtilsMock.when(JwtUtils::getUserId).thenReturn("test-user-id");
        com.openwallet.wallet.dto.TransactionListResponse response = com.openwallet.wallet.dto.TransactionListResponse.builder()
                .transactions(List.of(sampleTransactionItem(1L, "DEPOSIT")))
                .pagination(com.openwallet.wallet.dto.TransactionListResponse.PaginationMetadata.builder()
                        .page(0)
                        .size(20)
                        .totalElements(1)
                        .totalPages(1)
                        .hasNext(false)
                        .hasPrevious(false)
                        .build())
                .build();

        LocalDateTime fromDate = LocalDateTime.of(2024, 1, 1, 0, 0);
        LocalDateTime toDate = LocalDateTime.of(2024, 12, 31, 23, 59);

        Mockito.when(walletService.getWalletTransactions(
                eq(5L), eq(10L), eq(fromDate), eq(toDate), eq(null), eq(null),
                eq(0), eq(20), eq(null), eq("desc")
        )).thenReturn(response);

        mockMvc.perform(get("/api/v1/wallets/5/transactions")
                .header("X-Customer-Id", "10")
                .param("fromDate", "2024-01-01T00:00:00")
                .param("toDate", "2024-12-31T23:59:00")
                .param("page", "0")
                .param("size", "20"))
                .andExpect(status().isOk())
                .andExpect(content().json(objectMapper.writeValueAsString(response)));
        }
    }

    @Test
    @DisplayName("GET /api/v1/wallets/{id}/transactions with sorting returns sorted results")
    void getWalletTransactionsWithSortingShouldReturnSortedResults() throws Exception {
        try (MockedStatic<JwtUtils> jwtUtilsMock = Mockito.mockStatic(JwtUtils.class)) {
            jwtUtilsMock.when(JwtUtils::getUserId).thenReturn("test-user-id");
        com.openwallet.wallet.dto.TransactionListResponse response = com.openwallet.wallet.dto.TransactionListResponse.builder()
                .transactions(List.of(
                        sampleTransactionItem(1L, "DEPOSIT"),
                        sampleTransactionItem(2L, "WITHDRAWAL")
                ))
                .pagination(com.openwallet.wallet.dto.TransactionListResponse.PaginationMetadata.builder()
                        .page(0)
                        .size(20)
                        .totalElements(2)
                        .totalPages(1)
                        .hasNext(false)
                        .hasPrevious(false)
                        .build())
                .build();

        Mockito.when(walletService.getWalletTransactions(
                eq(5L), eq(10L), eq(null), eq(null), eq(null), eq(null),
                eq(0), eq(20), eq("amount"), eq("asc")
        )).thenReturn(response);

        mockMvc.perform(get("/api/v1/wallets/5/transactions")
                .header("X-Customer-Id", "10")
                .param("sortBy", "amount")
                .param("sortDirection", "asc")
                .param("page", "0")
                .param("size", "20"))
                .andExpect(status().isOk())
                .andExpect(content().json(objectMapper.writeValueAsString(response)));
        }
    }

    @Test
    @DisplayName("PUT /api/v1/wallets/{id}/suspend returns 200")
    void suspendWalletShouldReturnOk() throws Exception {
        try (MockedStatic<JwtUtils> jwtUtilsMock = Mockito.mockStatic(JwtUtils.class)) {
            jwtUtilsMock.when(JwtUtils::getUserId).thenReturn("test-user-id");

            WalletResponse response = sampleResponse(5L, "KES");
            response.setStatus("SUSPENDED");
            Mockito.when(walletService.suspendWallet(5L, 10L)).thenReturn(response);

            mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put("/api/v1/wallets/5/suspend")
                    .header("X-Customer-Id", "10"))
                    .andExpect(status().isOk())
                    .andExpect(content().json(objectMapper.writeValueAsString(response)));
        }
    }

    @Test
    @DisplayName("PUT /api/v1/wallets/{id}/activate returns 200")
    void activateWalletShouldReturnOk() throws Exception {
        try (MockedStatic<JwtUtils> jwtUtilsMock = Mockito.mockStatic(JwtUtils.class)) {
            jwtUtilsMock.when(JwtUtils::getUserId).thenReturn("test-user-id");

            WalletResponse response = sampleResponse(5L, "KES");
            response.setStatus("ACTIVE");
            Mockito.when(walletService.activateWallet(5L, 10L)).thenReturn(response);

            mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put("/api/v1/wallets/5/activate")
                    .header("X-Customer-Id", "10"))
                    .andExpect(status().isOk())
                    .andExpect(content().json(objectMapper.writeValueAsString(response)));
        }
    }

    private com.openwallet.wallet.dto.TransactionListResponse.TransactionItem sampleTransactionItem(Long id, String type) {
        return com.openwallet.wallet.dto.TransactionListResponse.TransactionItem.builder()
                .id(id)
                .transactionType(type)
                .status("COMPLETED")
                .amount(new BigDecimal("10.00"))
                .currency("KES")
                .fromWalletId(1L)
                .toWalletId(2L)
                .initiatedAt("2024-01-01T00:00:00")
                .completedAt("2024-01-01T00:01:00")
                .build();
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
