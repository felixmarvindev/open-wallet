package com.openwallet.wallet.client;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientResponseException;
import reactor.core.publisher.Mono;

import java.math.BigDecimal;

/**
 * Client for calling Ledger Service API.
 * Used for balance reconciliation by querying ledger entries.
 */
@Component
@RequiredArgsConstructor
@Slf4j
@SuppressWarnings("null")
public class LedgerServiceClient {

    private final WebClient.Builder webClientBuilder;

    @Value("${app.services.ledger.base-url:http://localhost:9004}")
    private String ledgerServiceBaseUrl;

    /**
     * Calculates the balance for a wallet from ledger entries.
     * 
     * @param walletId Wallet ID
     * @param accessToken JWT access token for authentication
     * @return Calculated balance from ledger entries
     * @throws LedgerServiceException if the call fails
     */
    public BigDecimal calculateBalanceFromLedger(Long walletId, String accessToken) {
        if (walletId == null) {
            throw new IllegalArgumentException("Wallet ID is required");
        }

        try {
            BalanceCalculationResponse response = webClientBuilder
                    .baseUrl(ledgerServiceBaseUrl)
                    .build()
                    .get()
                    .uri("/api/v1/ledger-entries/wallet/{walletId}/balance", walletId)
                    .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken)
                    .retrieve()
                    .bodyToMono(BalanceCalculationResponse.class)
                    .block();

            if (response == null || response.getBalance() == null) {
                log.warn("Ledger service returned null balance for walletId={}", walletId);
                throw new LedgerServiceException("Failed to calculate balance from ledger: null response");
            }

            log.debug("Calculated balance from ledger for walletId={}: {}", walletId, response.getBalance());
            return response.getBalance();

        } catch (WebClientResponseException e) {
            log.error("Failed to calculate balance from ledger for walletId={}: {} {}", 
                    walletId, e.getStatusCode(), e.getMessage());
            throw new LedgerServiceException(
                    "Failed to calculate balance from ledger: " + e.getStatusCode() + " " + e.getMessage(), e);
        } catch (Exception e) {
            log.error("Unexpected error calculating balance from ledger for walletId={}: {}", 
                    walletId, e.getMessage(), e);
            throw new LedgerServiceException("Failed to calculate balance from ledger", e);
        }
    }

    /**
     * Response DTO for balance calculation from ledger service.
     */
    public static class BalanceCalculationResponse {
        private Long walletId;
        private BigDecimal balance;

        public BalanceCalculationResponse() {
        }

        public BalanceCalculationResponse(Long walletId, BigDecimal balance) {
            this.walletId = walletId;
            this.balance = balance;
        }

        public Long getWalletId() {
            return walletId;
        }

        public void setWalletId(Long walletId) {
            this.walletId = walletId;
        }

        public BigDecimal getBalance() {
            return balance;
        }

        public void setBalance(BigDecimal balance) {
            this.balance = balance;
        }
    }

    /**
     * Exception thrown when ledger service call fails.
     */
    public static class LedgerServiceException extends RuntimeException {
        public LedgerServiceException(String message) {
            super(message);
        }

        public LedgerServiceException(String message, Throwable cause) {
            super(message, cause);
        }
    }
}


