package com.openwallet.integration.flows;

import com.openwallet.integration.IntegrationTestBase;
import com.openwallet.integration.infrastructure.ServiceRequirement;
import com.openwallet.integration.utils.OptimizedTestHelper;
import com.openwallet.integration.utils.TestDataValidator;
import com.openwallet.integration.utils.TestHttpClient;
import com.openwallet.integration.utils.TestUserManager;
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
 * 
 * Optimized: Only starts AUTH, CUSTOMER, and WALLET services.
 * Note: CUSTOMER service is required for wallet auto-creation via events.
 */
@Slf4j
@DisplayName("Wallet CRUD Operations")
@ServiceRequirement({
    ServiceRequirement.ServiceType.AUTH, 
    ServiceRequirement.ServiceType.CUSTOMER, 
    ServiceRequirement.ServiceType.WALLET
})
public class WalletCrudTest extends IntegrationTestBase {

    private OptimizedTestHelper testHelper;
    private TestHttpClient authClient;
    private TestHttpClient walletClient;
    private TestUserManager userManager;
    
    // Store usernames for reuse across tests
    private String multiWalletUserUsername;
    private String duplicateUserUsername;
    private String balanceUserUsername;
    private String user1Username;
    private String user2Username;

    @BeforeEach
    void setUp() {
        log.info("Starting optimized wallet CRUD test...");
        
        // Initialize helper (auto-starts only required services)
        testHelper = new OptimizedTestHelper(getInfrastructure());
        testHelper.startRequiredServices(this);
        
        // Validate services are running (fast-fail)
        testHelper.validateServices(
            ServiceRequirement.ServiceType.AUTH,
            ServiceRequirement.ServiceType.CUSTOMER,
            ServiceRequirement.ServiceType.WALLET
        );
        
        // Get clients
        authClient = testHelper.getClient(ServiceRequirement.ServiceType.AUTH);
        walletClient = testHelper.getClient(ServiceRequirement.ServiceType.WALLET);
        userManager = testHelper.getUserManager();
        
        // Create test users before tests run
        createTestUsers();
        
        log.info("✓ Required services started and ready for testing");
    }

    @AfterEach
    void tearDown() {
        if (testHelper != null) {
            testHelper.cleanup();
        }
    }
    
    /**
     * Creates test users that will be used by tests.
     * Users are created once before tests run, not during test execution.
     */
    private void createTestUsers() {
        log.info("Creating test users for wallet CRUD tests...");
        
        // Create users with unique names to avoid conflicts
        String timestamp = String.valueOf(System.currentTimeMillis());
        
        // Store usernames as instance variables
        multiWalletUserUsername = "multiwalletuser_" + timestamp;
        duplicateUserUsername = "duplicateuser_" + timestamp;
        balanceUserUsername = "balanceuser_" + timestamp;
        user1Username = "user1_" + timestamp;
        user2Username = "user2_" + timestamp;
        
        // Create users
        userManager.createUser(multiWalletUserUsername, multiWalletUserUsername + "@test.com");
        userManager.createUser(duplicateUserUsername, duplicateUserUsername + "@test.com");
        userManager.createUser(balanceUserUsername, balanceUserUsername + "@test.com");
        userManager.createUser(user1Username, user1Username + "@test.com");
        userManager.createUser(user2Username, user2Username + "@test.com");
        
        log.info("✓ Test users created: {}", timestamp);
    }

    @Test
    @DisplayName("User can create multiple wallets with different currencies")
    void userCanCreateMultipleWallets() throws Exception {
        // Use pre-created user
        TestDataValidator.requireUserExists(userManager, multiWalletUserUsername);
        String accessToken = userManager.getToken(multiWalletUserUsername);
        TestDataValidator.requireNotNull(accessToken, "Access token");

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
        // Use pre-created user
        TestDataValidator.requireUserExists(userManager, duplicateUserUsername);
        String accessToken = userManager.getToken(duplicateUserUsername);
        TestDataValidator.requireNotNull(accessToken, "Access token");

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
        // Use pre-created user
        TestDataValidator.requireUserExists(userManager, balanceUserUsername);
        String accessToken = userManager.getToken(balanceUserUsername);
        TestDataValidator.requireNotNull(accessToken, "Access token");

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
        // Use pre-created users
        TestDataValidator.requireUserExists(userManager, user1Username);
        TestDataValidator.requireUserExists(userManager, user2Username);
        
        String user1Token = userManager.getToken(user1Username);
        String user2Token = userManager.getToken(user2Username);
        TestDataValidator.requireNotNull(user1Token, "User1 access token");
        TestDataValidator.requireNotNull(user2Token, "User2 access token");
        
        Thread.sleep(2000);

        // Get user1's wallet ID
        TestHttpClient.HttpResponse user1Wallets = walletClient.get("/api/v1/wallets/me", user1Token);
        TestDataValidator.requireSuccess(user1Wallets, "Get user1 wallets");
        List<Map<String, Object>> wallets = walletClient.parseJsonArray(user1Wallets.getBody());
        Long user1WalletId = ((Number) wallets.get(0).get("id")).longValue();

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

}



