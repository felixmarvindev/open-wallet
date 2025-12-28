package com.openwallet.integration.flows;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.openwallet.integration.IntegrationTestBase;
import com.openwallet.integration.infrastructure.ServiceRequirement;
import com.openwallet.integration.utils.OptimizedTestHelper;
import com.openwallet.integration.utils.TestHttpClient;
import com.openwallet.integration.utils.TestUserManager;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.math.BigDecimal;
import java.util.HashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * End-to-end integration tests for wallet suspension and activation:
 * 1. Suspend wallet successfully
 * 2. Activate suspended wallet
 * 3. Prevent transactions on suspended wallets
 * 4. Verify events are published
 * 
 * Flow:
 * 1. Create user → Customer → Wallet
 * 2. Suspend wallet
 * 3. Attempt transaction (should fail)
 * 4. Activate wallet
 * 5. Attempt transaction (should succeed)
 * 
 * Optimized: Starts AUTH, CUSTOMER, WALLET, and LEDGER services.
 */
@Slf4j
@DisplayName("Wallet Suspension and Activation")
@ServiceRequirement({
    ServiceRequirement.ServiceType.AUTH,
    ServiceRequirement.ServiceType.CUSTOMER,
    ServiceRequirement.ServiceType.WALLET,
    ServiceRequirement.ServiceType.LEDGER
})
public class WalletSuspensionTest extends IntegrationTestBase {

    private OptimizedTestHelper testHelper;
    private TestHttpClient authClient;
    private TestHttpClient customerClient;
    private TestHttpClient walletClient;
    private TestHttpClient ledgerClient;
    private TestUserManager userManager;
    private ObjectMapper objectMapper;
    
    // Store test data
    private String testUserUsername;
    private String testUserAccessToken;
    private Long testCustomerId;
    private Long testWalletId;

    @BeforeEach
    void setUp() throws IOException {
        log.info("Starting wallet suspension test...");
        
        // Initialize helper (auto-starts only required services)
        testHelper = new OptimizedTestHelper(getInfrastructure());
        testHelper.startRequiredServices(this);
        
        // Validate services are running (fast-fail)
        testHelper.validateServices(
            ServiceRequirement.ServiceType.AUTH,
            ServiceRequirement.ServiceType.CUSTOMER,
            ServiceRequirement.ServiceType.WALLET,
            ServiceRequirement.ServiceType.LEDGER
        );
        
        // Get clients
        authClient = testHelper.getClient(ServiceRequirement.ServiceType.AUTH);
        customerClient = testHelper.getClient(ServiceRequirement.ServiceType.CUSTOMER);
        walletClient = testHelper.getClient(ServiceRequirement.ServiceType.WALLET);
        ledgerClient = testHelper.getClient(ServiceRequirement.ServiceType.LEDGER);
        userManager = testHelper.getUserManager();
        objectMapper = new ObjectMapper();
        
        // Create test user and setup
        createTestUser();
        createTestWallet();
        
        log.info("✓ Test setup complete - user: {}, wallet: {}", testUserUsername, testWalletId);
    }

    @AfterEach
    void tearDown() {
        if (testHelper != null) {
            testHelper.cleanup();
        }
    }

    /**
     * Creates a test user and authenticates to get access token.
     */
    private void createTestUser() throws IOException {
        long timestamp = System.currentTimeMillis();
        testUserUsername = "walletsuspend_" + timestamp;
        String email = testUserUsername + "@test.com";
        String password = "SecurePassword123!";
        
        // Register user
        Map<String, String> registerRequest = new HashMap<>();
        registerRequest.put("username", testUserUsername);
        registerRequest.put("email", email);
        registerRequest.put("password", password);
        
        TestHttpClient.HttpResponse registerResponse = authClient.post("/api/v1/auth/register", registerRequest);
        assertThat(registerResponse.getStatusCode()).isEqualTo(201);
        
        // Login to get token
        Map<String, String> loginRequest = new HashMap<>();
        loginRequest.put("username", testUserUsername);
        loginRequest.put("password", password);
        
        TestHttpClient.HttpResponse loginResponse = authClient.post("/api/v1/auth/login", loginRequest);
        assertThat(loginResponse.getStatusCode()).isEqualTo(200);
        
        Map<String, Object> loginBody = authClient.parseJson(loginResponse.getBody());
        testUserAccessToken = (String) loginBody.get("accessToken");
        assertThat(testUserAccessToken).isNotNull();
        
        // Create customer profile with unique phone number
        String userId = (String) loginBody.get("userId");
        String uniquePhoneSuffix = String.valueOf(timestamp).substring(Math.max(0, String.valueOf(timestamp).length() - 9));
        String phoneNumber = "+2547" + uniquePhoneSuffix;
        
        Map<String, Object> customerRequest = new HashMap<>();
        customerRequest.put("userId", userId);
        customerRequest.put("firstName", "Test");
        customerRequest.put("lastName", "User");
        customerRequest.put("email", email);
        customerRequest.put("phoneNumber", phoneNumber);
        customerRequest.put("dateOfBirth", "1990-01-01");
        customerRequest.put("address", "123 Test Street");
        
        TestHttpClient.HttpResponse customerResponse = customerClient.post(
                "/api/v1/customers", customerRequest, testUserAccessToken);
        assertThat(customerResponse.getStatusCode()).isEqualTo(201);
        
        Map<String, Object> customerBody = customerClient.parseJson(customerResponse.getBody());
        testCustomerId = ((Number) customerBody.get("id")).longValue();
        
        log.info("✓ Created test user: {} (customerId: {}, phone: {})", testUserUsername, testCustomerId, phoneNumber);
    }

    /**
     * Creates a test wallet for the user.
     */
    private void createTestWallet() throws IOException {
        Map<String, Object> walletRequest = new HashMap<>();
        walletRequest.put("currency", "KES");
        walletRequest.put("dailyLimit", 100000.00);
        walletRequest.put("monthlyLimit", 1000000.00);
        
        TestHttpClient.HttpResponse walletResponse = walletClient.post(
                "/api/v1/wallets", walletRequest, testUserAccessToken);
        assertThat(walletResponse.getStatusCode()).isEqualTo(201);
        
        Map<String, Object> walletBody = walletClient.parseJson(walletResponse.getBody());
        testWalletId = ((Number) walletBody.get("id")).longValue();
        
        log.info("✓ Created test wallet: {}", testWalletId);
    }

    @Test
    @DisplayName("PUT /api/v1/wallets/{id}/suspend should suspend wallet")
    void suspendWalletShouldSucceed() throws IOException {
        // When: Suspend wallet
        TestHttpClient.HttpResponse response = walletClient.put(
                "/api/v1/wallets/" + testWalletId + "/suspend", null, testUserAccessToken);
        
        // Then: Should return 200 with suspended status
        assertThat(response.getStatusCode()).isEqualTo(200);
        
        JsonNode responseBody = objectMapper.readTree(response.getBody());
        assertThat(responseBody.get("status").asText()).isEqualTo("SUSPENDED");
        assertThat(responseBody.get("id").asLong()).isEqualTo(testWalletId);
    }

    @Test
    @DisplayName("PUT /api/v1/wallets/{id}/activate should activate suspended wallet")
    void activateWalletShouldSucceed() throws IOException {
        // Given: Suspended wallet
        TestHttpClient.HttpResponse suspendResponse = walletClient.put(
                "/api/v1/wallets/" + testWalletId + "/suspend", null, testUserAccessToken);
        assertThat(suspendResponse.getStatusCode()).isEqualTo(200);
        
        // When: Activate wallet
        TestHttpClient.HttpResponse response = walletClient.put(
                "/api/v1/wallets/" + testWalletId + "/activate", null, testUserAccessToken);
        
        // Then: Should return 200 with active status
        assertThat(response.getStatusCode()).isEqualTo(200);
        
        JsonNode responseBody = objectMapper.readTree(response.getBody());
        assertThat(responseBody.get("status").asText()).isEqualTo("ACTIVE");
        assertThat(responseBody.get("id").asLong()).isEqualTo(testWalletId);
    }

    @Test
    @DisplayName("Suspended wallet should reject transactions")
    void suspendedWalletShouldRejectTransactions() throws IOException {
        // Given: Suspended wallet
        TestHttpClient.HttpResponse suspendResponse = walletClient.put(
                "/api/v1/wallets/" + testWalletId + "/suspend", null, testUserAccessToken);
        assertThat(suspendResponse.getStatusCode()).isEqualTo(200);
        
        // When: Attempt deposit to suspended wallet
        Map<String, Object> depositRequest = new HashMap<>();
        depositRequest.put("toWalletId", testWalletId);
        depositRequest.put("amount", 100.00);
        depositRequest.put("currency", "KES");
        depositRequest.put("idempotencyKey", "suspend-test-1");
        
        TestHttpClient.HttpResponse depositResponse = ledgerClient.post(
                "/api/v1/transactions/deposits", depositRequest, testUserAccessToken);
        
        // Then: Should return error
        assertThat(depositResponse.getStatusCode()).isEqualTo(400);
        
        JsonNode errorBody = objectMapper.readTree(depositResponse.getBody());
        String message = errorBody.has("message") ? errorBody.get("message").asText() : "";
        assertThat(message).contains("SUSPENDED");
        assertThat(message).contains("cannot process transactions");
    }

    @Test
    @DisplayName("Activated wallet should accept transactions")
    void activatedWalletShouldAcceptTransactions() throws IOException {
        // Given: Suspended then activated wallet
        TestHttpClient.HttpResponse suspendResponse = walletClient.put(
                "/api/v1/wallets/" + testWalletId + "/suspend", null, testUserAccessToken);
        assertThat(suspendResponse.getStatusCode()).isEqualTo(200);
        
        TestHttpClient.HttpResponse activateResponse = walletClient.put(
                "/api/v1/wallets/" + testWalletId + "/activate", null, testUserAccessToken);
        assertThat(activateResponse.getStatusCode()).isEqualTo(200);
        
        // When: Attempt deposit to activated wallet
        Map<String, Object> depositRequest = new HashMap<>();
        depositRequest.put("toWalletId", testWalletId);
        depositRequest.put("amount", 100.00);
        depositRequest.put("currency", "KES");
        depositRequest.put("idempotencyKey", "activate-test-1");
        
        TestHttpClient.HttpResponse depositResponse = ledgerClient.post(
                "/api/v1/transactions/deposits", depositRequest, testUserAccessToken);
        
        // Then: Should succeed
        assertThat(depositResponse.getStatusCode()).isEqualTo(201);
        
        JsonNode responseBody = objectMapper.readTree(depositResponse.getBody());
        assertThat(responseBody.get("status").asText()).isEqualTo("COMPLETED");
    }

    @Test
    @DisplayName("Suspended wallet should reject transfers")
    void suspendedWalletShouldRejectTransfers() throws IOException {
        // Given: Two wallets, one suspended
        TestHttpClient.HttpResponse suspendResponse = walletClient.put(
                "/api/v1/wallets/" + testWalletId + "/suspend", null, testUserAccessToken);
        assertThat(suspendResponse.getStatusCode()).isEqualTo(200);
        
        // Create second wallet
        Map<String, Object> walletRequest2 = new HashMap<>();
        walletRequest2.put("currency", "KES");
        TestHttpClient.HttpResponse walletResponse2 = walletClient.post(
                "/api/v1/wallets", walletRequest2, testUserAccessToken);
        Map<String, Object> walletBody2 = walletClient.parseJson(walletResponse2.getBody());
        Long wallet2Id = ((Number) walletBody2.get("id")).longValue();
        
        // When: Attempt transfer from suspended wallet
        Map<String, Object> transferRequest = new HashMap<>();
        transferRequest.put("fromWalletId", testWalletId);
        transferRequest.put("toWalletId", wallet2Id);
        transferRequest.put("amount", 50.00);
        transferRequest.put("currency", "KES");
        transferRequest.put("idempotencyKey", "suspend-transfer-1");
        
        TestHttpClient.HttpResponse transferResponse = ledgerClient.post(
                "/api/v1/transactions/transfers", transferRequest, testUserAccessToken);
        
        // Then: Should return error
        assertThat(transferResponse.getStatusCode()).isEqualTo(400);
        
        JsonNode errorBody = objectMapper.readTree(transferResponse.getBody());
        String message = errorBody.has("message") ? errorBody.get("message").asText() : "";
        assertThat(message).contains("SUSPENDED");
    }
}

