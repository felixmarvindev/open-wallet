package com.openwallet.ledger.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.openwallet.ledger.dto.DepositRequest;
import com.openwallet.ledger.dto.TransactionResponse;
import com.openwallet.ledger.dto.TransferRequest;
import com.openwallet.ledger.dto.WithdrawalRequest;
import com.openwallet.ledger.service.TransactionService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(controllers = TransactionController.class)
@AutoConfigureMockMvc(addFilters = false)
@ActiveProfiles("test")
@SuppressWarnings({ "DataFlowIssue", "null" })
class TransactionControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private TransactionService transactionService;

    @Test
    @DisplayName("POST /transactions/deposits returns 201")
    void createDepositShouldReturnCreated() throws Exception {
        DepositRequest request = DepositRequest.builder()
                .toWalletId(5L)
                .amount(new BigDecimal("10.00"))
                .currency("KES")
                .idempotencyKey("dep-1")
                .build();

        TransactionResponse response = sampleResponse(1L, "DEPOSIT");
        Mockito.when(transactionService.createDeposit(any(DepositRequest.class))).thenReturn(response);

        mockMvc.perform(post("/api/v1/transactions/deposits")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(content().json(objectMapper.writeValueAsString(response)));
    }

    @Test
    @DisplayName("POST /transactions/withdrawals returns 201")
    void createWithdrawalShouldReturnCreated() throws Exception {
        WithdrawalRequest request = WithdrawalRequest.builder()
                .fromWalletId(6L)
                .amount(new BigDecimal("5.00"))
                .currency("USD")
                .idempotencyKey("wd-1")
                .build();

        TransactionResponse response = sampleResponse(2L, "WITHDRAWAL");
        Mockito.when(transactionService.createWithdrawal(any(WithdrawalRequest.class))).thenReturn(response);

        mockMvc.perform(post("/api/v1/transactions/withdrawals")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(content().json(objectMapper.writeValueAsString(response)));
    }

    @Test
    @DisplayName("POST /transactions/transfers returns 201")
    void createTransferShouldReturnCreated() throws Exception {
        TransferRequest request = TransferRequest.builder()
                .fromWalletId(7L)
                .toWalletId(8L)
                .amount(new BigDecimal("15.00"))
                .currency("EUR")
                .idempotencyKey("tr-1")
                .build();

        TransactionResponse response = sampleResponse(3L, "TRANSFER");
        Mockito.when(transactionService.createTransfer(any(TransferRequest.class))).thenReturn(response);

        mockMvc.perform(post("/api/v1/transactions/transfers")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(content().json(objectMapper.writeValueAsString(response)));
    }

    @Test
    @DisplayName("GET /transactions/{id} returns 200")
    void getTransactionShouldReturnOk() throws Exception {
        TransactionResponse response = sampleResponse(10L, "DEPOSIT");
        Mockito.when(transactionService.getTransaction(eq(10L))).thenReturn(response);

        mockMvc.perform(get("/api/v1/transactions/10"))
                .andExpect(status().isOk())
                .andExpect(content().json(objectMapper.writeValueAsString(response)));
    }

    @Test
    @DisplayName("POST /transactions/deposits fails validation when missing currency")
    void createDepositShouldFailValidation() throws Exception {
        String payload = "{\"toWalletId\":5,\"amount\":10.0,\"currency\":\"\"}";

        mockMvc.perform(post("/api/v1/transactions/deposits")
                .contentType(MediaType.APPLICATION_JSON)
                .content(payload))
                .andExpect(status().isBadRequest());
    }

    private TransactionResponse sampleResponse(Long id, String type) {
        return TransactionResponse.builder()
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
}
