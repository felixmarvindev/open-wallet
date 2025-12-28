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
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * End-to-end integration tests for transaction history and querying:
 * 1. Query transactions by wallet ID
 * 2. Query transactions with date range filtering
 * 3. Query transactions with status filtering
 * 4. Query transactions with transaction type filtering
 * 5. Test pagination
 * 6. Test sorting
 * 7. Test wallet-specific transaction history endpoint
 * 
 * Flow:
 * 1. Create user → Customer → Wallet
 * 2. Create multiple transactions (deposits, withdrawals, transfers)
 * 3. Query transactions with various filters
 * 4. Verify results match expectations
 * 
 * Optimized: Starts AUTH, CUSTOMER, WALLET, and LEDGER services.
 * Pre-creates test users before tests run.
 */
@Slf4j
@DisplayName("Transaction History and Querying")
@ServiceRequirement({
    ServiceRequirement.ServiceType.AUTH,
    ServiceRequirement.ServiceType.CUSTOMER,
    ServiceRequirement.ServiceType.WALLET,
    ServiceRequirement.ServiceType.LEDGER
})
public class TransactionHistoryTest extends IntegrationTestBase {

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
    private Long testWallet2Id;
    private Map<Long, Map<String, Object>> createdTransactions;

    @BeforeEach
    void setUp() throws IOException {
        log.info("Starting transaction history test...");
        
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
        createTestWallets();
        createTestTransactions();
        
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
        testUserUsername = "txhistory_" + timestamp;
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
        // Use last 9 digits of timestamp to create unique phone number
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
     * Creates test wallets for the user.
     */
    private void createTestWallets() throws IOException {
        // Create first wallet
        Map<String, Object> walletRequest1 = new HashMap<>();
        walletRequest1.put("currency", "KES");
        walletRequest1.put("dailyLimit", 100000.00);
        walletRequest1.put("monthlyLimit", 1000000.00);
        
        TestHttpClient.HttpResponse walletResponse1 = walletClient.post(
                "/api/v1/wallets", walletRequest1, testUserAccessToken);
        assertThat(walletResponse1.getStatusCode()).isEqualTo(201);
        
        Map<String, Object> walletBody1 = walletClient.parseJson(walletResponse1.getBody());
        testWalletId = ((Number) walletBody1.get("id")).longValue();
        
        // Create second wallet for transfers
        Map<String, Object> walletRequest2 = new HashMap<>();
        walletRequest2.put("currency", "KES");
        
        TestHttpClient.HttpResponse walletResponse2 = walletClient.post(
                "/api/v1/wallets", walletRequest2, testUserAccessToken);
        assertThat(walletResponse2.getStatusCode()).isEqualTo(201);
        
        Map<String, Object> walletBody2 = walletClient.parseJson(walletResponse2.getBody());
        testWallet2Id = ((Number) walletBody2.get("id")).longValue();
        
        log.info("✓ Created test wallets: {} and {}", testWalletId, testWallet2Id);
    }

    /**
     * Creates test transactions for querying.
     */
    private void createTestTransactions() throws IOException {
        createdTransactions = new HashMap<>();
        
        // Create deposit
        Map<String, Object> depositRequest = new HashMap<>();
        depositRequest.put("toWalletId", testWalletId);
        depositRequest.put("amount", 100.00);
        depositRequest.put("currency", "KES");
        depositRequest.put("idempotencyKey", "hist-dep-1");
        
        TestHttpClient.HttpResponse depositResponse = ledgerClient.post(
                "/api/v1/transactions/deposits", depositRequest, testUserAccessToken);
        assertThat(depositResponse.getStatusCode()).isEqualTo(201);
        Map<String, Object> depositBody = ledgerClient.parseJson(depositResponse.getBody());
        Long depositId = ((Number) depositBody.get("id")).longValue();
        createdTransactions.put(depositId, depositBody);
        
        // Small delay to ensure different timestamps
        try {
            Thread.sleep(100);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        
        // Create withdrawal
        Map<String, Object> withdrawalRequest = new HashMap<>();
        withdrawalRequest.put("fromWalletId", testWalletId);
        withdrawalRequest.put("amount", 50.00);
        withdrawalRequest.put("currency", "KES");
        withdrawalRequest.put("idempotencyKey", "hist-wd-1");
        
        TestHttpClient.HttpResponse withdrawalResponse = ledgerClient.post(
                "/api/v1/transactions/withdrawals", withdrawalRequest, testUserAccessToken);
        assertThat(withdrawalResponse.getStatusCode()).isEqualTo(201);
        Map<String, Object> withdrawalBody = ledgerClient.parseJson(withdrawalResponse.getBody());
        Long withdrawalId = ((Number) withdrawalBody.get("id")).longValue();
        createdTransactions.put(withdrawalId, withdrawalBody);
        
        // Small delay
        try {
            Thread.sleep(100);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        
        // Create transfer
        Map<String, Object> transferRequest = new HashMap<>();
        transferRequest.put("fromWalletId", testWalletId);
        transferRequest.put("toWalletId", testWallet2Id);
        transferRequest.put("amount", 25.00);
        transferRequest.put("currency", "KES");
        transferRequest.put("idempotencyKey", "hist-tr-1");
        
        TestHttpClient.HttpResponse transferResponse = ledgerClient.post(
                "/api/v1/transactions/transfers", transferRequest, testUserAccessToken);
        assertThat(transferResponse.getStatusCode()).isEqualTo(201);
        Map<String, Object> transferBody = ledgerClient.parseJson(transferResponse.getBody());
        Long transferId = ((Number) transferBody.get("id")).longValue();
        createdTransactions.put(transferId, transferBody);
        
        log.info("✓ Created {} test transactions", createdTransactions.size());
    }

    @Test
    @DisplayName("GET /api/v1/transactions should return all transactions")
    void getTransactionsShouldReturnAllTransactions() throws IOException {
        // When: Query all transactions
        Map<String, String> queryParams = new HashMap<>();
        TestHttpClient.HttpResponse response = ledgerClient.get(
                "/api/v1/transactions", queryParams, testUserAccessToken);
        
        // Then: Should return 200 with transaction list
        assertThat(response.getStatusCode()).isEqualTo(200);
        
        JsonNode responseBody = objectMapper.readTree(response.getBody());
        assertThat(responseBody.has("transactions")).isTrue();
        assertThat(responseBody.has("pagination")).isTrue();
        
        JsonNode transactions = responseBody.get("transactions");
        assertThat(transactions.isArray()).isTrue();
        assertThat(transactions.size()).isGreaterThanOrEqualTo(createdTransactions.size());
        
        JsonNode pagination = responseBody.get("pagination");
        assertThat(pagination.get("page").asInt()).isEqualTo(0);
        assertThat(pagination.get("size").asInt()).isEqualTo(20);
        assertThat(pagination.get("totalElements").asLong()).isGreaterThanOrEqualTo(createdTransactions.size());
    }

    @Test
    @DisplayName("GET /api/v1/transactions?walletId=X should filter by wallet")
    void getTransactionsShouldFilterByWalletId() throws IOException {
        // When: Query transactions for testWalletId
        Map<String, String> queryParams = new HashMap<>();
        queryParams.put("walletId", String.valueOf(testWalletId));
        
        TestHttpClient.HttpResponse response = ledgerClient.get(
                "/api/v1/transactions", queryParams, testUserAccessToken);
        
        // Then: Should return only transactions involving testWalletId
        assertThat(response.getStatusCode()).isEqualTo(200);
        
        JsonNode responseBody = objectMapper.readTree(response.getBody());
        JsonNode transactions = responseBody.get("transactions");
        
        assertThat(transactions.isArray()).isTrue();
        for (JsonNode tx : transactions) {
            Long fromWalletId = tx.has("fromWalletId") && !tx.get("fromWalletId").isNull() 
                    ? tx.get("fromWalletId").asLong() : null;
            Long toWalletId = tx.has("toWalletId") && !tx.get("toWalletId").isNull() 
                    ? tx.get("toWalletId").asLong() : null;
            
            assertThat(fromWalletId != null && fromWalletId.equals(testWalletId) ||
                      toWalletId != null && toWalletId.equals(testWalletId)).isTrue();
        }
    }

    @Test
    @DisplayName("GET /api/v1/transactions?status=COMPLETED should filter by status")
    void getTransactionsShouldFilterByStatus() throws IOException {
        // When: Query only COMPLETED transactions
        Map<String, String> queryParams = new HashMap<>();
        queryParams.put("status", "COMPLETED");
        
        TestHttpClient.HttpResponse response = ledgerClient.get(
                "/api/v1/transactions", queryParams, testUserAccessToken);
        
        // Then: Should return only COMPLETED transactions
        assertThat(response.getStatusCode()).isEqualTo(200);
        
        JsonNode responseBody = objectMapper.readTree(response.getBody());
        JsonNode transactions = responseBody.get("transactions");
        
        assertThat(transactions.isArray()).isTrue();
        for (JsonNode tx : transactions) {
            assertThat(tx.get("status").asText()).isEqualTo("COMPLETED");
        }
    }

    @Test
    @DisplayName("GET /api/v1/transactions?transactionType=DEPOSIT should filter by type")
    void getTransactionsShouldFilterByTransactionType() throws IOException {
        // When: Query only DEPOSIT transactions
        Map<String, String> queryParams = new HashMap<>();
        queryParams.put("transactionType", "DEPOSIT");
        
        TestHttpClient.HttpResponse response = ledgerClient.get(
                "/api/v1/transactions", queryParams, testUserAccessToken);
        
        // Then: Should return only DEPOSIT transactions
        assertThat(response.getStatusCode()).isEqualTo(200);
        
        JsonNode responseBody = objectMapper.readTree(response.getBody());
        JsonNode transactions = responseBody.get("transactions");
        
        assertThat(transactions.isArray()).isTrue();
        for (JsonNode tx : transactions) {
            assertThat(tx.get("transactionType").asText()).isEqualTo("DEPOSIT");
        }
    }

    @Test
    @DisplayName("GET /api/v1/transactions should support pagination")
    void getTransactionsShouldSupportPagination() throws IOException {
        // When: Query first page with size 2
        Map<String, String> queryParams = new HashMap<>();
        queryParams.put("page", "0");
        queryParams.put("size", "2");
        
        TestHttpClient.HttpResponse page1Response = ledgerClient.get(
                "/api/v1/transactions", queryParams, testUserAccessToken);
        
        // Then: Should return 2 transactions
        assertThat(page1Response.getStatusCode()).isEqualTo(200);
        
        JsonNode page1Body = objectMapper.readTree(page1Response.getBody());
        JsonNode page1Transactions = page1Body.get("transactions");
        assertThat(page1Transactions.size()).isLessThanOrEqualTo(2);
        
        JsonNode page1Pagination = page1Body.get("pagination");
        assertThat(page1Pagination.get("page").asInt()).isEqualTo(0);
        assertThat(page1Pagination.get("size").asInt()).isEqualTo(2);
        assertThat(page1Pagination.get("hasNext").asBoolean()).isTrue();
        
        // When: Query second page
        queryParams.put("page", "1");
        TestHttpClient.HttpResponse page2Response = ledgerClient.get(
                "/api/v1/transactions", queryParams, testUserAccessToken);
        
        // Then: Should return next page
        assertThat(page2Response.getStatusCode()).isEqualTo(200);
        JsonNode page2Body = objectMapper.readTree(page2Response.getBody());
        JsonNode page2Pagination = page2Body.get("pagination");
        assertThat(page2Pagination.get("page").asInt()).isEqualTo(1);
        assertThat(page2Pagination.get("hasPrevious").asBoolean()).isTrue();
    }

    @Test
    @DisplayName("GET /api/v1/transactions should support sorting")
    void getTransactionsShouldSupportSorting() throws IOException {
        // When: Query sorted by amount ascending
        Map<String, String> queryParams = new HashMap<>();
        queryParams.put("sortBy", "amount");
        queryParams.put("sortDirection", "asc");
        
        TestHttpClient.HttpResponse response = ledgerClient.get(
                "/api/v1/transactions", queryParams, testUserAccessToken);
        
        // Then: Transactions should be sorted by amount
        assertThat(response.getStatusCode()).isEqualTo(200);
        
        JsonNode responseBody = objectMapper.readTree(response.getBody());
        JsonNode transactions = responseBody.get("transactions");
        
        if (transactions.size() > 1) {
            BigDecimal prevAmount = null;
            for (JsonNode tx : transactions) {
                BigDecimal amount = new BigDecimal(tx.get("amount").asText());
                if (prevAmount != null) {
                    assertThat(amount).isGreaterThanOrEqualTo(prevAmount);
                }
                prevAmount = amount;
            }
        }
    }

    @Test
    @DisplayName("GET /api/v1/transactions should cap page size at 100")
    void getTransactionsShouldCapPageSize() throws IOException {
        // When: Query with size > 100
        Map<String, String> queryParams = new HashMap<>();
        queryParams.put("size", "200");
        
        TestHttpClient.HttpResponse response = ledgerClient.get(
                "/api/v1/transactions", queryParams, testUserAccessToken);
        
        // Then: Should cap at 100
        assertThat(response.getStatusCode()).isEqualTo(200);
        
        JsonNode responseBody = objectMapper.readTree(response.getBody());
        JsonNode pagination = responseBody.get("pagination");
        assertThat(pagination.get("size").asInt()).isEqualTo(100);
    }

    @Test
    @DisplayName("GET /api/v1/wallets/{id}/transactions should return wallet transactions")
    void getWalletTransactionsShouldReturnWalletTransactions() throws IOException {
        // When: Query transactions for wallet
        Map<String, String> queryParams = new HashMap<>();
        TestHttpClient.HttpResponse response = walletClient.get(
                "/api/v1/wallets/" + testWalletId + "/transactions", queryParams, testUserAccessToken);
        
        // Then: Should return transactions for the wallet
        assertThat(response.getStatusCode()).isEqualTo(200);
        
        JsonNode responseBody = objectMapper.readTree(response.getBody());
        assertThat(responseBody.has("transactions")).isTrue();
        assertThat(responseBody.has("pagination")).isTrue();
        
        JsonNode transactions = responseBody.get("transactions");
        assertThat(transactions.isArray()).isTrue();
        
        // Verify all transactions involve the wallet
        for (JsonNode tx : transactions) {
            Long fromWalletId = tx.has("fromWalletId") && !tx.get("fromWalletId").isNull() 
                    ? tx.get("fromWalletId").asLong() : null;
            Long toWalletId = tx.has("toWalletId") && !tx.get("toWalletId").isNull() 
                    ? tx.get("toWalletId").asLong() : null;
            
            assertThat(fromWalletId != null && fromWalletId.equals(testWalletId) ||
                      toWalletId != null && toWalletId.equals(testWalletId)).isTrue();
        }
    }

    @Test
    @DisplayName("GET /api/v1/wallets/{id}/transactions should validate wallet ownership")
    void getWalletTransactionsShouldValidateOwnership() throws IOException {
        // Setup: Create another user with their own wallet
        long timestamp = System.currentTimeMillis();
        String otherUser = "txhistory_other_" + timestamp;
        String otherEmail = otherUser + "@test.com";
        
        Map<String, String> registerRequest = new HashMap<>();
        registerRequest.put("username", otherUser);
        registerRequest.put("email", otherEmail);
        registerRequest.put("password", "SecurePassword123!");
        
        authClient.post("/api/v1/auth/register", registerRequest);
        
        Map<String, String> loginRequest = new HashMap<>();
        loginRequest.put("username", otherUser);
        loginRequest.put("password", "SecurePassword123!");
        
        TestHttpClient.HttpResponse loginResponse = authClient.post("/api/v1/auth/login", loginRequest);
        Map<String, Object> loginBody = authClient.parseJson(loginResponse.getBody());
        String otherUserToken = (String) loginBody.get("accessToken");
        
        // Create customer profile for other user with unique phone
        String userId = (String) loginBody.get("userId");
        String uniquePhoneSuffix = String.valueOf(timestamp).substring(Math.max(0, String.valueOf(timestamp).length() - 9));
        String phoneNumber = "+2548" + uniquePhoneSuffix; // Different prefix to avoid conflicts
        
        Map<String, Object> customerRequest = new HashMap<>();
        customerRequest.put("userId", userId);
        customerRequest.put("firstName", "Other");
        customerRequest.put("lastName", "User");
        customerRequest.put("email", otherEmail);
        customerRequest.put("phoneNumber", phoneNumber);
        customerRequest.put("dateOfBirth", "1990-01-01");
        customerRequest.put("address", "456 Other Street");
        
        customerClient.post("/api/v1/customers", customerRequest, otherUserToken);
        
        // When: Other user tries to access first user's wallet transactions
        Map<String, String> queryParams = new HashMap<>();
        TestHttpClient.HttpResponse response = walletClient.get(
                "/api/v1/wallets/" + testWalletId + "/transactions", queryParams, otherUserToken);
        
        // Then: Should return 404 (wallet not found for that user)
        assertThat(response.getStatusCode()).isEqualTo(404);
    }

    @Test
    @DisplayName("GET /api/v1/wallets/{id}/transactions should support filtering")
    void getWalletTransactionsShouldSupportFiltering() throws IOException {
        // When: Query wallet transactions with status filter
        Map<String, String> queryParams = new HashMap<>();
        queryParams.put("status", "COMPLETED");
        queryParams.put("transactionType", "DEPOSIT");
        
        TestHttpClient.HttpResponse response = walletClient.get(
                "/api/v1/wallets/" + testWalletId + "/transactions", queryParams, testUserAccessToken);
        
        // Then: Should return only matching transactions
        assertThat(response.getStatusCode()).isEqualTo(200);
        
        JsonNode responseBody = objectMapper.readTree(response.getBody());
        JsonNode transactions = responseBody.get("transactions");
        
        for (JsonNode tx : transactions) {
            assertThat(tx.get("status").asText()).isEqualTo("COMPLETED");
            assertThat(tx.get("transactionType").asText()).isEqualTo("DEPOSIT");
        }
    }
}

