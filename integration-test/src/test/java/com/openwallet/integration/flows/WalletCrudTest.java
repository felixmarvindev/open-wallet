package com.openwallet.integration.flows;

import com.openwallet.integration.IntegrationTestBase;
import com.openwallet.integration.infrastructure.ServiceContainerManager;
import com.openwallet.integration.utils.TestHttpClient;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tests wallet CRUD operations:
 * - Create multiple wallets (KES, USD, EUR)
 * - Get wallet by ID
 * - Get balance
 * - Cannot create duplicate currency
 * - Cannot access other user's wallet
 */
@Slf4j
@DisplayName("Wallet CRUD Operations")
public class WalletCrudTest extends IntegrationTestBase {

    private ServiceContainerManager serviceManager;
    private TestHttpClient authClient;
    private TestHttpClient walletClient;

    @BeforeEach
    void setUp() {
        log.info("Starting services for wallet CRUD test...");
        serviceManager = new ServiceContainerManager(getInfrastructure());
        serviceManager.startAll();

        authClient = new TestHttpClient(serviceManager.getAuthService().getBaseUrl());
        walletClient = new TestHttpClient(serviceManager.getWalletService().getBaseUrl());

        log.info("✓ Services ready");
    }

    @AfterEach
    void tearDown() {
        if (serviceManager != null) serviceManager.stopAll();
    }

    @Test
    @DisplayName("User can create multiple wallets with different currencies")
    void userCanCreateMultipleWallets() throws Exception {
        // Register and login
        String[] credentials = registerAndLogin("multiwalletuser");
        String accessToken = credentials[0];

        // Wait for auto-created KES wallet
        Thread.sleep(2000);

        // Get initial wallets (should have 1 auto-created)
        TestHttpClient.HttpResponse initialWallets = walletClient.get("/api/v1/wallets/me", accessToken);
        List<Map<String, Object>> initial = walletClient.parseJsonArray(initialWallets.getBody());
        log.info("Initial wallets: {}", initial.size());

        // Create USD wallet
        Map<String, String> usdRequest = new HashMap<>();
        usdRequest.put("currency", "USD");
        TestHttpClient.HttpResponse usdResponse = walletClient.post("/api/v1/wallets", usdRequest, accessToken);
        assertThat(usdResponse.getStatusCode()).isEqualTo(201);
        Map<String, Object> usdWallet = walletClient.parseJson(usdResponse.getBody());
        assertThat(usdWallet.get("currency")).isEqualTo("USD");
        log.info("✓ USD wallet created: {}", usdWallet.get("id"));

        // Create EUR wallet
        Map<String, String> eurRequest = new HashMap<>();
        eurRequest.put("currency", "EUR");
        TestHttpClient.HttpResponse eurResponse = walletClient.post("/api/v1/wallets", eurRequest, accessToken);
        assertThat(eurResponse.getStatusCode()).isEqualTo(201);
        Map<String, Object> eurWallet = walletClient.parseJson(eurResponse.getBody());
        assertThat(eurWallet.get("currency")).isEqualTo("EUR");
        log.info("✓ EUR wallet created: {}", eurWallet.get("id"));

        // Get all wallets - should have 3 now (KES auto-created + USD + EUR)
        TestHttpClient.HttpResponse allWallets = walletClient.get("/api/v1/wallets/me", accessToken);
        List<Map<String, Object>> wallets = walletClient.parseJsonArray(allWallets.getBody());
        assertThat(wallets).hasSizeGreaterThanOrEqualTo(3);

        // Verify currencies
        List<String> currencies = wallets.stream()
                .map(w -> (String) w.get("currency"))
                .toList();
        assertThat(currencies).contains("KES", "USD", "EUR");
        log.info("✓ All wallets verified: {}", currencies);
    }

    @Test
    @DisplayName("Cannot create duplicate currency wallet")
    void cannotCreateDuplicateCurrencyWallet() throws Exception {
        String[] credentials = registerAndLogin("duplicateuser");
        String accessToken = credentials[0];

        Thread.sleep(2000); // Wait for auto-created KES wallet

        // Try to create another KES wallet
        Map<String, String> kesRequest = new HashMap<>();
        kesRequest.put("currency", "KES");
        TestHttpClient.HttpResponse response = walletClient.post("/api/v1/wallets", kesRequest, accessToken);

        // Should get 4xx error (400 or 409)
        assertThat(response.getStatusCode()).isGreaterThanOrEqualTo(400).isLessThan(500);
        log.info("✓ Duplicate currency rejected with status: {}", response.getStatusCode());
    }

    @Test
    @DisplayName("User can get wallet balance")
    void userCanGetWalletBalance() throws Exception {
        String[] credentials = registerAndLogin("balanceuser");
        String accessToken = credentials[0];

        Thread.sleep(2000);

        // Get wallets
        TestHttpClient.HttpResponse walletsResponse = walletClient.get("/api/v1/wallets/me", accessToken);
        List<Map<String, Object>> wallets = walletClient.parseJsonArray(walletsResponse.getBody());
        assertThat(wallets).isNotEmpty();

        Long walletId = ((Number) wallets.get(0).get("id")).longValue();

        // Get balance
        TestHttpClient.HttpResponse balanceResponse = walletClient.get(
                "/api/v1/wallets/" + walletId + "/balance",
                accessToken
        );
        assertThat(balanceResponse.getStatusCode()).isEqualTo(200);

        Map<String, Object> balance = walletClient.parseJson(balanceResponse.getBody());
        assertThat(balance.get("balance")).isNotNull();
        assertThat(balance.get("currency")).isNotNull();
        assertThat(new BigDecimal(balance.get("balance").toString())).isEqualByComparingTo(BigDecimal.ZERO);
        log.info("✓ Balance retrieved: {} {}", balance.get("balance"), balance.get("currency"));
    }

    @Test
    @DisplayName("User cannot access another user's wallet")
    void userCannotAccessOtherUsersWallet() throws Exception {
        // Create user 1
        String[] user1Creds = registerAndLogin("user1");
        String user1Token = user1Creds[0];
        Thread.sleep(2000);

        // Get user1's wallet ID
        TestHttpClient.HttpResponse user1Wallets = walletClient.get("/api/v1/wallets/me", user1Token);
        List<Map<String, Object>> wallets = walletClient.parseJsonArray(user1Wallets.getBody());
        Long user1WalletId = ((Number) wallets.get(0).get("id")).longValue();

        // Create user 2
        String[] user2Creds = registerAndLogin("user2");
        String user2Token = user2Creds[0];

        Thread.sleep(2000);

        // User 2 tries to access User 1's wallet
        TestHttpClient.HttpResponse response = walletClient.get(
                "/api/v1/wallets/" + user1WalletId,
                user2Token
        );

        // Should get 403 Forbidden or 404 Not Found
        assertThat(response.getStatusCode()).isIn(403, 404);
        log.info("✓ Cross-user access denied with status: {}", response.getStatusCode());
    }

    // Helper method
    private String[] registerAndLogin(String username) throws Exception {
        String email = username + "@test.com";
        String password = "Test123!@#";

        // Register
        Map<String, String> registerRequest = new HashMap<>();
        registerRequest.put("username", username);
        registerRequest.put("email", email);
        registerRequest.put("password", password);
        authClient.post("/api/v1/auth/register", registerRequest);

        // Login
        Map<String, String> loginRequest = new HashMap<>();
        loginRequest.put("username", username);
        loginRequest.put("password", password);
        TestHttpClient.HttpResponse loginResponse = authClient.post("/api/v1/auth/login", loginRequest);
        Map<String, Object> loginBody = authClient.parseJson(loginResponse.getBody());
        String token = (String) loginBody.get("accessToken");

        return new String[]{token, email};
    }
}



