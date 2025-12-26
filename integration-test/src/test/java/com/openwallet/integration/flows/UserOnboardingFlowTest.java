package com.openwallet.integration.flows;

import com.openwallet.integration.IntegrationTestBase;
import com.openwallet.integration.infrastructure.ServiceRequirement;
import com.openwallet.integration.utils.KafkaEventVerifier;
import com.openwallet.integration.utils.OptimizedTestHelper;
import com.openwallet.integration.utils.TestDataValidator;
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
 * End-to-end integration test for user onboarding flow:
 * 1. Register user → Auth Service
 * 2. Login → Get JWT token
 * 3. Create customer profile → Customer Service
 * 4. Verify USER_REGISTERED event published → Kafka
 * 
 * This test validates the complete flow across multiple services.
 * Optimized: Only starts AUTH and CUSTOMER services (not WALLET).
 */
@Slf4j
@DisplayName("User Onboarding Flow")
@ServiceRequirement({ServiceRequirement.ServiceType.AUTH, ServiceRequirement.ServiceType.CUSTOMER})
public class UserOnboardingFlowTest extends IntegrationTestBase {

    private OptimizedTestHelper testHelper;
    private TestHttpClient authClient;
    private TestHttpClient customerClient;
    private KafkaEventVerifier userEventsVerifier;

    @BeforeEach
    void setUp() {
        log.info("Starting optimized user onboarding flow test...");
        
        // Initialize helper (auto-starts only AUTH and CUSTOMER services)
        testHelper = new OptimizedTestHelper(getInfrastructure());
        testHelper.startRequiredServices(this);
        
        // Validate services are running (fast-fail)
        testHelper.validateServices(
            ServiceRequirement.ServiceType.AUTH,
            ServiceRequirement.ServiceType.CUSTOMER
        );
        
        // Get clients
        authClient = testHelper.getClient(ServiceRequirement.ServiceType.AUTH);
        customerClient = testHelper.getClient(ServiceRequirement.ServiceType.CUSTOMER);

        // Set up Kafka event verifier for user-events topic
        userEventsVerifier = new KafkaEventVerifier(
                getInfrastructure().getKafkaBootstrapServers(),
                "user-events"
        );
        
        log.info("✓ Required services started and ready for testing");
    }

    @AfterEach
    void tearDown() {
        log.info("Cleaning up test resources...");
        if (userEventsVerifier != null) {
            userEventsVerifier.close();
        }
        if (testHelper != null) {
            testHelper.cleanup();
        }
    }

    @Test
    @DisplayName("Complete user onboarding flow: Register → Login → Create Customer → Verify Events")
    void completeUserOnboardingFlow() throws Exception {
        // Step 1: Register a new user
        log.info("Step 1: Registering new user...");
        Map<String, String> registerRequest = new HashMap<>();
        registerRequest.put("username", "testuser_" + System.currentTimeMillis());
        registerRequest.put("email", "testuser_" + System.currentTimeMillis() + "@example.com");
        registerRequest.put("password", "SecurePassword123!");

        TestHttpClient.HttpResponse registerResponse = authClient.post("/api/v1/auth/register", registerRequest);
        assertThat(registerResponse.getStatusCode()).isEqualTo(201);
        log.info("User registered successfully");

        // Parse response to get user ID
        Map<String, Object> registerBody = authClient.parseJson(registerResponse.getBody());
        String userId = (String) registerBody.get("userId");
        String username = (String) registerBody.get("username");
        String email = (String) registerBody.get("email");
        
        assertThat(userId).isNotNull();
        assertThat(username).isNotNull();
        log.info("Registered user - ID: {}, Username: {}, Email: {}", userId, username, email);

        // Step 2: Verify USER_REGISTERED event was published
        log.info("Step 2: Verifying USER_REGISTERED event...");
        KafkaEventVerifier.ConsumerRecordWrapper<String, String> userRegisteredEvent = 
                userEventsVerifier.verifyEventContains("userId", userId, 10);
        
        assertThat(userRegisteredEvent).isNotNull();
        assertThat(userRegisteredEvent.value()).contains("\"eventType\":\"USER_REGISTERED\"");
        assertThat(userRegisteredEvent.value()).contains("\"username\":\"" + username + "\"");
        assertThat(userRegisteredEvent.value()).contains("\"email\":\"" + email + "\"");
        log.info("✓ USER_REGISTERED event verified");

        // Step 3: Login to get JWT token
        log.info("Step 3: Logging in...");
        Map<String, String> loginRequest = new HashMap<>();
        loginRequest.put("username", username);
        loginRequest.put("password", "SecurePassword123!");

        TestHttpClient.HttpResponse loginResponse = authClient.post("/api/v1/auth/login", loginRequest);
        assertThat(loginResponse.getStatusCode()).isEqualTo(200);
        
        Map<String, Object> loginBody = authClient.parseJson(loginResponse.getBody());
        String accessToken = (String) loginBody.get("accessToken");
        assertThat(accessToken).isNotNull();
        log.info("✓ Login successful, received access token");

        // Step 4: Create customer profile using JWT token
        log.info("Step 4: Creating customer profile...");
        Map<String, Object> createCustomerRequest = new HashMap<>();
        createCustomerRequest.put("userId", userId);
        createCustomerRequest.put("firstName", "John");
        createCustomerRequest.put("lastName", "Doe");
        createCustomerRequest.put("email", email);
        createCustomerRequest.put("phoneNumber", "+254712345678");
        createCustomerRequest.put("dateOfBirth", "1990-01-01");
        createCustomerRequest.put("address", "123 Test Street, Nairobi");

        TestHttpClient.HttpResponse customerResponse = customerClient.post(
                "/api/v1/customers", 
                createCustomerRequest, 
                accessToken
        );
        
        assertThat(customerResponse.getStatusCode()).isEqualTo(201);
        log.info("✓ Customer profile created successfully");

        // Step 5: Verify customer was created
        Map<String, Object> customerBody = customerClient.parseJson(customerResponse.getBody());
        assertThat(customerBody.get("id")).isNotNull();
        assertThat(customerBody.get("userId")).isEqualTo(userId);
        assertThat(customerBody.get("email")).isEqualTo(email);
        log.info("✓ Customer verification passed - ID: {}", customerBody.get("id"));

        log.info("✓ Complete user onboarding flow successful!");
    }

    @Test
    @DisplayName("User registration publishes USER_REGISTERED event")
    void userRegistrationPublishesEvent() throws Exception {
        log.info("Testing user registration event publishing...");

        // Register user
        Map<String, String> registerRequest = new HashMap<>();
        registerRequest.put("username", "eventtest_" + System.currentTimeMillis());
        registerRequest.put("email", "eventtest_" + System.currentTimeMillis() + "@example.com");
        registerRequest.put("password", "SecurePassword123!");

        TestHttpClient.HttpResponse registerResponse = authClient.post("/api/v1/auth/register", registerRequest);
        assertThat(registerResponse.getStatusCode()).isEqualTo(201);

        Map<String, Object> registerBody = authClient.parseJson(registerResponse.getBody());
        String userId = (String) registerBody.get("userId");

        // Verify event was published with correct userId (single verification)
        KafkaEventVerifier.ConsumerRecordWrapper<String, String> event = 
                userEventsVerifier.verifyEventContains("userId", userId, 10);
        assertThat(event).isNotNull();
        assertThat(event.value()).contains("\"eventType\":\"USER_REGISTERED\"");
        assertThat(event.value()).contains("\"userId\":\"" + userId + "\"");
        
        log.info("✓ USER_REGISTERED event verified with userId: {}", userId);
    }
}

