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

import java.util.HashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Integration test for automatic wallet creation flow.
 * 
 * Flow:
 * 1. User registers → Auth Service
 * 2. USER_REGISTERED event → Kafka
 * 3. Customer Service listens → Creates Customer
 * 4. CUSTOMER_CREATED event → Kafka
 * 5. Wallet Service listens → Creates Wallet
 * 6. WALLET_CREATED event → Kafka
 * 7. Verify wallet exists with correct initial limits
 */
@Slf4j
@DisplayName("Wallet Creation Flow")
public class WalletCreationFlowTest extends IntegrationTestBase {

    private ServiceContainerManager serviceManager;
    private TestHttpClient authClient;
    private TestHttpClient walletClient;
    private KafkaEventVerifier userEventsVerifier;
    private KafkaEventVerifier customerEventsVerifier;
    private KafkaEventVerifier walletEventsVerifier;

    @BeforeEach
    void setUp() {
        log.info("Starting services for wallet creation flow test...");
        serviceManager = new ServiceContainerManager(getInfrastructure());
        serviceManager.startAll();
        
        authClient = new TestHttpClient(serviceManager.getAuthService().getBaseUrl());
        walletClient = new TestHttpClient(serviceManager.getWalletService().getBaseUrl());
        
        userEventsVerifier = new KafkaEventVerifier(
                getInfrastructure().getKafkaBootstrapServers(),
                "user-events"
        );
        customerEventsVerifier = new KafkaEventVerifier(
                getInfrastructure().getKafkaBootstrapServers(),
                "customer-events"
        );
        walletEventsVerifier = new KafkaEventVerifier(
                getInfrastructure().getKafkaBootstrapServers(),
                "wallet-events"
        );
        
        log.info("✓ All services started and ready for testing");
    }

    @AfterEach
    void tearDown() {
        log.info("Cleaning up test resources...");
        if (userEventsVerifier != null) {
            userEventsVerifier.close();
        }
        if (customerEventsVerifier != null) {
            customerEventsVerifier.close();
        }
        if (walletEventsVerifier != null) {
            walletEventsVerifier.close();
        }
        if (serviceManager != null) {
            serviceManager.stopAll();
        }
    }

    @Test
    @DisplayName("User registration automatically creates customer and wallet")
    void userRegistrationCreatesCustomerAndWallet() throws Exception {
        // Step 1: Register user
        log.info("Step 1: Registering new user...");
        Map<String, String> registerRequest = new HashMap<>();
        String uniqueId = "walletuser_" + System.currentTimeMillis();
        registerRequest.put("username", uniqueId);
        registerRequest.put("email", uniqueId + "@example.com");
        registerRequest.put("password", "SecurePassword123!");

        TestHttpClient.HttpResponse registerResponse = authClient.post("/api/v1/auth/register", registerRequest);
        assertThat(registerResponse.getStatusCode()).isEqualTo(201);
        
        Map<String, Object> registerBody = authClient.parseJson(registerResponse.getBody());
        String userId = (String) registerBody.get("userId");
        String username = (String) registerBody.get("username");
        String email = (String) registerBody.get("email");
        
        assertThat(userId).isNotNull();
        assertThat(username).isNotNull();
        assertThat(email).isNotNull();
        log.info("User registered - ID: {}, Username: {}, Email: {}", userId, username, email);

        // Step 2: Verify USER_REGISTERED event
        log.info("Step 2: Verifying USER_REGISTERED event...");
        KafkaEventVerifier.ConsumerRecordWrapper<String, String> userEvent = 
                userEventsVerifier.verifyEventContains("userId", userId, 10);
        
        assertThat(userEvent).isNotNull();
        assertThat(userEvent.value()).contains("\"eventType\":\"USER_REGISTERED\"");
        assertThat(userEvent.value()).contains("\"username\":\"" + username + "\"");
        assertThat(userEvent.value()).contains("\"email\":\"" + email + "\"");
        log.info("✓ USER_REGISTERED event verified");

        // Step 3: Verify CUSTOMER_CREATED event
        log.info("Step 3: Verifying CUSTOMER_CREATED event (auto-created by listener)...");
        KafkaEventVerifier.ConsumerRecordWrapper<String, String> customerEvent = 
                customerEventsVerifier.verifyEventContains("userId", userId, 15);
        
        assertThat(customerEvent).isNotNull();
        assertThat(customerEvent.value()).contains("\"eventType\":\"CUSTOMER_CREATED\"");
        
        Map<String, Object> customerData = authClient.parseJson(customerEvent.value());
        Object customerIdObj = customerData.get("customerId");
        assertThat(customerIdObj).isNotNull();
        
        // Handle both Integer and Long
        Long customerId = customerIdObj instanceof Integer 
            ? ((Integer) customerIdObj).longValue() 
            : (Long) customerIdObj;
        
        assertThat(customerId).isNotNull();
        log.info("✓ CUSTOMER_CREATED event verified - Customer ID: {}", customerId);

        // Step 4: Verify WALLET_CREATED event
        log.info("Step 4: Verifying WALLET_CREATED event (auto-created by listener)...");
        KafkaEventVerifier.ConsumerRecordWrapper<String, String> walletEvent = 
                walletEventsVerifier.verifyEventContains("customerId", customerId.toString(), 15);
        
        assertThat(walletEvent).isNotNull();
        assertThat(walletEvent.value()).contains("\"eventType\":\"WALLET_CREATED\"");
        
        Map<String, Object> walletData = authClient.parseJson(walletEvent.value());
        Object walletIdObj = walletData.get("walletId");
        assertThat(walletIdObj).isNotNull();
        
        Long walletId = walletIdObj instanceof Integer 
            ? ((Integer) walletIdObj).longValue() 
            : (Long) walletIdObj;
        
        assertThat(walletId).isNotNull();
        log.info("✓ WALLET_CREATED event verified - Wallet ID: {}", walletId);

        // Step 5: Login to get JWT token
        log.info("Step 5: Logging in to get access token...");
        Map<String, String> loginRequest = new HashMap<>();
        loginRequest.put("username", username);
        loginRequest.put("password", "SecurePassword123!");

        TestHttpClient.HttpResponse loginResponse = authClient.post("/api/v1/auth/login", loginRequest);
        assertThat(loginResponse.getStatusCode()).isEqualTo(200);
        
        Map<String, Object> loginBody = authClient.parseJson(loginResponse.getBody());
        String accessToken = (String) loginBody.get("accessToken");
        assertThat(accessToken).isNotNull();
        log.info("✓ Login successful, received access token");

        // Step 6: Get wallet details and verify
        log.info("Step 6: Retrieving wallet details via API...");
        TestHttpClient.HttpResponse walletResponse = walletClient.get(
                "/api/v1/wallets/me",
                accessToken
        );
        
        assertThat(walletResponse.getStatusCode()).isEqualTo(200);
        
        // Parse response as array of wallets
        java.util.List<Map<String, Object>> wallets = walletClient.parseJsonArray(walletResponse.getBody());
        
        assertThat(wallets).isNotEmpty();
        assertThat(wallets).hasSize(1);
        
        Map<String, Object> wallet = wallets.get(0);
        
        // Verify wallet details
        Object retrievedWalletId = wallet.get("id");
        Long retrievedWalletIdLong = retrievedWalletId instanceof Integer 
            ? ((Integer) retrievedWalletId).longValue() 
            : (Long) retrievedWalletId;
        
        assertThat(retrievedWalletIdLong).isEqualTo(walletId);
        assertThat(wallet.get("customerId")).isNotNull();
        assertThat(wallet.get("balance")).isEqualTo(0.0);
        assertThat(wallet.get("status")).isEqualTo("ACTIVE");
        assertThat(wallet.get("currency")).isEqualTo("KES");
        
        // Verify initial limits (KYC pending)
        Double dailyLimit = getDoubleValue(wallet.get("dailyLimit"));
        Double monthlyLimit = getDoubleValue(wallet.get("monthlyLimit"));
        
        assertThat(dailyLimit).isEqualTo(5000.00);
        assertThat(monthlyLimit).isEqualTo(20000.00);
        
        log.info("✓ Wallet verified:");
        log.info("  - Wallet ID: {}", walletId);
        log.info("  - Customer ID: {}", customerId);
        log.info("  - Balance: {}", wallet.get("balance"));
        log.info("  - Currency: {}", wallet.get("currency"));
        log.info("  - Daily Limit: {} (KYC pending)", dailyLimit);
        log.info("  - Monthly Limit: {} (KYC pending)", monthlyLimit);
        log.info("  - Status: {}", wallet.get("status"));

        log.info("========================================");
        log.info("✓ Complete automatic wallet creation flow successful!");
        log.info("========================================");
    }

    /**
     * Helper method to convert various number types to Double.
     */
    private Double getDoubleValue(Object value) {
        if (value == null) {
            return null;
        }
        if (value instanceof Double) {
            return (Double) value;
        }
        if (value instanceof Integer) {
            return ((Integer) value).doubleValue();
        }
        if (value instanceof Long) {
            return ((Long) value).doubleValue();
        }
        if (value instanceof String) {
            try {
                return Double.parseDouble((String) value);
            } catch (NumberFormatException e) {
                return null;
            }
        }
        return null;
    }
}

