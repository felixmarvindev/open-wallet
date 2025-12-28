package com.openwallet.integration.flows;

import com.openwallet.integration.IntegrationTestBase;
import com.openwallet.integration.infrastructure.ServiceRequirement;
import com.openwallet.integration.utils.KafkaEventVerifier;
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
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * End-to-end KYC verification flow:
 * 1. User registers → Wallet created with LOW limits
 * 2. User initiates KYC
 * 3. KYC verification webhook → KYC_VERIFIED event
 * 4. Wallet Service updates limits to HIGH
 * 5. Verify via API
 * 
 * Optimized: Only starts AUTH, CUSTOMER, and WALLET services (all required for event flow).
 * Pre-creates test users before tests run.
 */
@Slf4j
@DisplayName("KYC Verification Flow")
@ServiceRequirement({
    ServiceRequirement.ServiceType.AUTH,
    ServiceRequirement.ServiceType.CUSTOMER,
    ServiceRequirement.ServiceType.WALLET
})
public class KycVerificationFlowTest extends IntegrationTestBase {

    private OptimizedTestHelper testHelper;
    private TestHttpClient authClient;
    private TestHttpClient customerClient;
    private TestHttpClient walletClient;
    private TestUserManager userManager;
    private KafkaEventVerifier kycEventsVerifier;
    
    // Store usernames for reuse across tests
    private String kycUserUsername;
    private String multiKycUserUsername;

    @BeforeEach
    void setUp() {
        log.info("Starting optimized KYC verification test...");
        
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
        customerClient = testHelper.getClient(ServiceRequirement.ServiceType.CUSTOMER);
        walletClient = testHelper.getClient(ServiceRequirement.ServiceType.WALLET);
        userManager = testHelper.getUserManager();
        
        // Set up Kafka event verifier
        kycEventsVerifier = new KafkaEventVerifier(
            getInfrastructure().getKafkaBootstrapServers(), 
            "kyc-events"
        );
        
        // Create test users before tests run
        createTestUsers();
        
        log.info("✓ Required services started and ready for testing");
    }

    @AfterEach
    void tearDown() {
        if (kycEventsVerifier != null) {
            kycEventsVerifier.close();
        }
        if (testHelper != null) {
            testHelper.cleanup();
        }
    }
    
    /**
     * Creates test users that will be used by tests.
     * Users are created once before tests run, not during test execution.
     */
    private void createTestUsers() {
        log.info("Creating test users for KYC verification tests...");
        
        // Create users with unique names to avoid conflicts
        String timestamp = String.valueOf(System.currentTimeMillis());
        
        // Store usernames as instance variables
        kycUserUsername = "kycuser_" + timestamp;
        multiKycUserUsername = "multikycuser_" + timestamp;
        
        // Create users
        userManager.createUser(kycUserUsername, kycUserUsername + "@test.com");
        userManager.createUser(multiKycUserUsername, multiKycUserUsername + "@test.com");
        
        log.info("✓ Test users created: {}", timestamp);
    }

    @Test
    @DisplayName("KYC verification increases wallet limits from low to high")
    void kycVerificationIncreasesLimits() throws Exception {
        // Use pre-created user
        log.info("Step 1: Using pre-created user...");
        TestDataValidator.requireUserExists(userManager, kycUserUsername);
        String accessToken = userManager.getToken(kycUserUsername);
        TestDataValidator.requireNotNull(accessToken, "Access token");

        // Step 2: Wait for async wallet creation
        log.info("Step 2: Waiting for wallet creation...");
        Thread.sleep(3000);

        // Step 3: Verify LOW limits initially
        log.info("Step 3: Verifying initial LOW limits...");
        TestHttpClient.HttpResponse initialWallets = walletClient.get("/api/v1/wallets/me", accessToken);
        TestDataValidator.requireSuccess(initialWallets, "Get initial wallets");
        List<Map<String, Object>> wallets = walletClient.parseJsonArray(initialWallets.getBody());
        assertThat(wallets).hasSizeGreaterThanOrEqualTo(1);

        Map<String, Object> wallet = wallets.get(0);
        Long walletId = ((Number) wallet.get("id")).longValue();
        BigDecimal initialDaily = new BigDecimal(wallet.get("dailyLimit").toString());
        BigDecimal initialMonthly = new BigDecimal(wallet.get("monthlyLimit").toString());

        assertThat(initialDaily).isEqualByComparingTo(new BigDecimal("5000.00"));
        assertThat(initialMonthly).isEqualByComparingTo(new BigDecimal("20000.00"));
        log.info("✓ Initial limits: daily={}, monthly={}", initialDaily, initialMonthly);

        // Step 4: Get customer ID
        log.info("Step 4: Getting customer ID...");
        TestHttpClient.HttpResponse customerResponse = customerClient.get("/api/v1/customers/me", accessToken);
        TestDataValidator.requireSuccess(customerResponse, "Get customer profile");
        Map<String, Object> customer = customerClient.parseJson(customerResponse.getBody());
        Long customerId = ((Number) customer.get("id")).longValue();
        TestDataValidator.requireNotNull(customerId, "Customer ID");

        // Step 5: Initiate KYC
        log.info("Step 5: Initiating KYC...");
        Map<String, Object> kycRequest = new HashMap<>();
        kycRequest.put("documents", Map.of("idNumber", "12345678", "idType", "NATIONAL_ID"));
        TestHttpClient.HttpResponse kycInitResponse = customerClient.post(
                "/api/v1/customers/me/kyc/initiate",
                kycRequest,
                accessToken
        );
        TestDataValidator.requireSuccess(kycInitResponse, "Initiate KYC");
        log.info("✓ KYC initiated");

        // Step 6: Simulate KYC verification webhook
        log.info("Step 6: Simulating KYC verification webhook...");
        Map<String, Object> webhookRequest = new HashMap<>();
        webhookRequest.put("customerId", customerId);
        webhookRequest.put("providerReference", "KYC-TEST-" + customerId);
        webhookRequest.put("status", "VERIFIED");
        webhookRequest.put("verifiedAt", LocalDateTime.now().toString());

        TestHttpClient.HttpResponse webhookResponse = customerClient.post(
                "/api/v1/customers/kyc/webhook",
                webhookRequest
        );
        TestDataValidator.requireSuccess(webhookResponse, "KYC webhook");
        log.info("✓ KYC webhook processed");

        // Step 7: Verify KYC_VERIFIED event published
        log.info("Step 7: Verifying KYC_VERIFIED event...");
        boolean kycVerifiedFound = kycEventsVerifier.verifyEventType("KYC_VERIFIED", 10);
        assertThat(kycVerifiedFound).isTrue();
        log.info("✓ KYC_VERIFIED event verified");

        // Step 8: Wait for async limit update
        log.info("Step 8: Waiting for wallet limit update...");
        Thread.sleep(3000);

        // Step 9: Verify HIGH limits now
        log.info("Step 9: Verifying updated HIGH limits...");
        TestHttpClient.HttpResponse updatedWallets = walletClient.get("/api/v1/wallets/me", accessToken);
        TestDataValidator.requireSuccess(updatedWallets, "Get updated wallets");
        List<Map<String, Object>> updated = walletClient.parseJsonArray(updatedWallets.getBody());
        assertThat(updated).hasSizeGreaterThanOrEqualTo(1);

        Map<String, Object> updatedWallet = updated.get(0);
        BigDecimal updatedDaily = new BigDecimal(updatedWallet.get("dailyLimit").toString());
        BigDecimal updatedMonthly = new BigDecimal(updatedWallet.get("monthlyLimit").toString());

        assertThat(updatedDaily).isEqualByComparingTo(new BigDecimal("50000.00"));
        assertThat(updatedMonthly).isEqualByComparingTo(new BigDecimal("200000.00"));

        log.info("✓ Limits updated:");
        log.info("  Daily:   {} → {}", initialDaily, updatedDaily);
        log.info("  Monthly: {} → {}", initialMonthly, updatedMonthly);
        log.info("✓ KYC verification flow complete!");
    }

    @Test
    @DisplayName("Multiple wallets all get updated after KYC")
    void multipleWalletsGetUpdatedAfterKyc() throws Exception {
        // Use pre-created user
        TestDataValidator.requireUserExists(userManager, multiKycUserUsername);
        String accessToken = userManager.getToken(multiKycUserUsername);
        TestDataValidator.requireNotNull(accessToken, "Access token");

        Thread.sleep(2000);

        // Create KES wallet (KES auto-created, but we can verify it exists)
        Map<String, String> kesRequest = new HashMap<>();
        kesRequest.put("currency", "KES");
        walletClient.post("/api/v1/wallets", kesRequest, accessToken);

        // Get customer ID
        TestHttpClient.HttpResponse customerResponse = customerClient.get("/api/v1/customers/me", accessToken);
        TestDataValidator.requireSuccess(customerResponse, "Get customer profile");
        Map<String, Object> customer = customerClient.parseJson(customerResponse.getBody());
        Long customerId = ((Number) customer.get("id")).longValue();
        TestDataValidator.requireNotNull(customerId, "Customer ID");

        // Initiate and verify KYC
        Map<String, Object> kycRequest = new HashMap<>();
        kycRequest.put("documents", Map.of("idNumber", "12345678", "idType", "NATIONAL_ID"));
        TestHttpClient.HttpResponse kycInitResponse = customerClient.post(
                "/api/v1/customers/me/kyc/initiate", 
                kycRequest, 
                accessToken
        );
        TestDataValidator.requireSuccess(kycInitResponse, "Initiate KYC");

        Map<String, Object> webhookRequest = new HashMap<>();
        webhookRequest.put("customerId", customerId);
        webhookRequest.put("providerReference", "KYC-TEST-" + customerId);
        webhookRequest.put("status", "VERIFIED");
        webhookRequest.put("verifiedAt", LocalDateTime.now().toString());
        TestHttpClient.HttpResponse webhookResponse = customerClient.post(
                "/api/v1/customers/kyc/webhook", 
                webhookRequest
        );
        TestDataValidator.requireSuccess(webhookResponse, "KYC webhook");

        Thread.sleep(3000); // Wait for update

        // Verify ALL wallets have high limits
        TestHttpClient.HttpResponse walletsResponse = walletClient.get("/api/v1/wallets/me", accessToken);
        TestDataValidator.requireSuccess(walletsResponse, "Get wallets");
        List<Map<String, Object>> wallets = walletClient.parseJsonArray(walletsResponse.getBody());
        assertThat(wallets).hasSizeGreaterThanOrEqualTo(2);

        for (Map<String, Object> wallet : wallets) {
            BigDecimal daily = new BigDecimal(wallet.get("dailyLimit").toString());
            BigDecimal monthly = new BigDecimal(wallet.get("monthlyLimit").toString());
            assertThat(daily).isEqualByComparingTo(new BigDecimal("50000.00"));
            assertThat(monthly).isEqualByComparingTo(new BigDecimal("200000.00"));
            log.info("✓ {} wallet has high limits", wallet.get("currency"));
        }
    }

}

