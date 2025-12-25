package com.openwallet.integration.flows;

import com.openwallet.integration.IntegrationTestBase;
import com.openwallet.integration.infrastructure.ServiceContainerManager;
import com.openwallet.integration.utils.KafkaEventVerifier;
import com.openwallet.integration.utils.TestHttpClient;
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
 */
@Slf4j
@DisplayName("KYC Verification Flow")
public class KycVerificationFlowTest extends IntegrationTestBase {

    private ServiceContainerManager serviceManager;
    private TestHttpClient authClient;
    private TestHttpClient customerClient;
    private TestHttpClient walletClient;
    private KafkaEventVerifier kycEventsVerifier;

    @BeforeEach
    void setUp() {
        log.info("Starting services for KYC verification test...");
        serviceManager = new ServiceContainerManager(getInfrastructure());
        serviceManager.startAll();

        authClient = new TestHttpClient(serviceManager.getAuthService().getBaseUrl());
        customerClient = new TestHttpClient(serviceManager.getCustomerService().getBaseUrl());
        walletClient = new TestHttpClient(serviceManager.getWalletService().getBaseUrl());
        kycEventsVerifier = new KafkaEventVerifier(getInfrastructure().getKafkaBootstrapServers(), "kyc-events");

        log.info("✓ Services ready");
    }

    @AfterEach
    void tearDown() {
        if (kycEventsVerifier != null) kycEventsVerifier.close();
        if (serviceManager != null) serviceManager.stopAll();
    }

    @Test
    @DisplayName("KYC verification increases wallet limits from low to high")
    void kycVerificationIncreasesLimits() throws Exception {
        // Step 1: Register user
        log.info("Step 1: Registering user...");
        String username = "kycuser_" + System.currentTimeMillis();
        String email = username + "@test.com";
        String password = "Test123!@#";

        Map<String, String> registerRequest = new HashMap<>();
        registerRequest.put("username", username);
        registerRequest.put("email", email);
        registerRequest.put("password", password);
        authClient.post("/api/v1/auth/register", registerRequest);

        // Step 2: Login
        log.info("Step 2: Logging in...");
        Map<String, String> loginRequest = new HashMap<>();
        loginRequest.put("username", username);
        loginRequest.put("password", password);
        TestHttpClient.HttpResponse loginResponse = authClient.post("/api/v1/auth/login", loginRequest);
        Map<String, Object> loginBody = authClient.parseJson(loginResponse.getBody());
        String accessToken = (String) loginBody.get("accessToken");

        // Step 3: Wait for async wallet creation
        log.info("Step 3: Waiting for wallet creation...");
        Thread.sleep(3000);

        // Step 4: Verify LOW limits initially
        log.info("Step 4: Verifying initial LOW limits...");
        TestHttpClient.HttpResponse initialWallets = walletClient.get("/api/v1/wallets/me", accessToken);
        List<Map<String, Object>> wallets = walletClient.parseJsonArray(initialWallets.getBody());
        assertThat(wallets).hasSizeGreaterThanOrEqualTo(1);

        Map<String, Object> wallet = wallets.get(0);
        Long walletId = ((Number) wallet.get("id")).longValue();
        BigDecimal initialDaily = new BigDecimal(wallet.get("dailyLimit").toString());
        BigDecimal initialMonthly = new BigDecimal(wallet.get("monthlyLimit").toString());

        assertThat(initialDaily).isEqualByComparingTo(new BigDecimal("5000.00"));
        assertThat(initialMonthly).isEqualByComparingTo(new BigDecimal("20000.00"));
        log.info("✓ Initial limits: daily={}, monthly={}", initialDaily, initialMonthly);

        // Step 5: Get customer ID
        log.info("Step 5: Getting customer ID...");
        TestHttpClient.HttpResponse customerResponse = customerClient.get("/api/v1/customers/me", accessToken);
        Map<String, Object> customer = customerClient.parseJson(customerResponse.getBody());
        Long customerId = ((Number) customer.get("id")).longValue();

        // Step 6: Initiate KYC
        log.info("Step 6: Initiating KYC...");
        Map<String, Object> kycRequest = new HashMap<>();
        kycRequest.put("documents", Map.of("idNumber", "12345678", "idType", "NATIONAL_ID"));
        TestHttpClient.HttpResponse kycInitResponse = customerClient.post(
                "/api/v1/customers/me/kyc/initiate",
                kycRequest,
                accessToken
        );
        assertThat(kycInitResponse.getStatusCode()).isEqualTo(200);
        log.info("✓ KYC initiated");

        // Step 7: Simulate KYC verification webhook
        log.info("Step 7: Simulating KYC verification webhook...");
        Map<String, Object> webhookRequest = new HashMap<>();
        webhookRequest.put("customerId", customerId);
        webhookRequest.put("providerReference", "KYC-TEST-" + customerId);
        webhookRequest.put("status", "VERIFIED");
        webhookRequest.put("verifiedAt", LocalDateTime.now().toString());

        TestHttpClient.HttpResponse webhookResponse = customerClient.post(
                "/api/v1/customers/kyc/webhook",
                webhookRequest
        );
        assertThat(webhookResponse.getStatusCode()).isEqualTo(200);
        log.info("✓ KYC webhook processed");

        // Step 8: Verify KYC_VERIFIED event published
        log.info("Step 8: Verifying KYC_VERIFIED event...");
        boolean kycVerifiedFound = kycEventsVerifier.verifyEventType("KYC_VERIFIED", 10);
        assertThat(kycVerifiedFound).isTrue();
        log.info("✓ KYC_VERIFIED event verified");

        // Step 9: Wait for async limit update
        log.info("Step 9: Waiting for wallet limit update...");
        Thread.sleep(3000);

        // Step 10: Verify HIGH limits now
        log.info("Step 10: Verifying updated HIGH limits...");
        TestHttpClient.HttpResponse updatedWallets = walletClient.get("/api/v1/wallets/me", accessToken);
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
        // Register and login
        String username = "multikycuser_" + System.currentTimeMillis();
        String[] credentials = registerAndLogin(username);
        String accessToken = credentials[0];

        Thread.sleep(2000);

        // Create USD wallet (KES auto-created)
        Map<String, String> usdRequest = new HashMap<>();
        usdRequest.put("currency", "USD");
        walletClient.post("/api/v1/wallets", usdRequest, accessToken);

        // Get customer ID
        TestHttpClient.HttpResponse customerResponse = customerClient.get("/api/v1/customers/me", accessToken);
        Map<String, Object> customer = customerClient.parseJson(customerResponse.getBody());
        Long customerId = ((Number) customer.get("id")).longValue();

        // Initiate and verify KYC
        Map<String, Object> kycRequest = new HashMap<>();
        kycRequest.put("documents", Map.of("idNumber", "12345678", "idType", "NATIONAL_ID"));
        customerClient.post("/api/v1/customers/me/kyc/initiate", kycRequest, accessToken);

        Map<String, Object> webhookRequest = new HashMap<>();
        webhookRequest.put("customerId", customerId);
        webhookRequest.put("providerReference", "KYC-TEST-" + customerId);
        webhookRequest.put("status", "VERIFIED");
        webhookRequest.put("verifiedAt", LocalDateTime.now().toString());
        customerClient.post("/api/v1/customers/kyc/webhook", webhookRequest);

        Thread.sleep(3000); // Wait for update

        // Verify ALL wallets have high limits
        TestHttpClient.HttpResponse walletsResponse = walletClient.get("/api/v1/wallets/me", accessToken);
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

    // Helper
    private String[] registerAndLogin(String username) throws Exception {
        String email = username + "@test.com";
        String password = "Test123!@#";

        Map<String, String> registerRequest = new HashMap<>();
        registerRequest.put("username", username);
        registerRequest.put("email", email);
        registerRequest.put("password", password);
        authClient.post("/api/v1/auth/register", registerRequest);

        Map<String, String> loginRequest = new HashMap<>();
        loginRequest.put("username", username);
        loginRequest.put("password", password);
        TestHttpClient.HttpResponse loginResponse = authClient.post("/api/v1/auth/login", loginRequest);
        Map<String, Object> loginBody = authClient.parseJson(loginResponse.getBody());
        String token = (String) loginBody.get("accessToken");

        return new String[]{token, email};
    }
}

