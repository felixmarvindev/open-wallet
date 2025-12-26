package com.openwallet.integration.flows;

import com.openwallet.integration.IntegrationTestBase;
import com.openwallet.integration.infrastructure.ServiceContainerManager;
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

import java.util.HashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tests customer profile CRUD operations:
 * - Update profile (name, email, phone)
 * - Validate profile updates
 * - Cannot update to existing email
 */
@Slf4j
@DisplayName("Customer Profile CRUD Operations")
@ServiceRequirement({ServiceRequirement.ServiceType.AUTH, ServiceRequirement.ServiceType.CUSTOMER})
public class CustomerProfileCrudTest extends IntegrationTestBase {

    private OptimizedTestHelper testHelper;
    private TestHttpClient customerClient;
    private TestUserManager userManager;

    // Store usernames as instance variables so they can be reused across test methods
    private String updateUserUsername;
    private String validationUserUsername;
    private String phoneValidationUserUsername;
    private String requiredFieldsUserUsername;

    @BeforeEach
    void setUp() {
        log.info("Setting up optimized test...");

        // Initialize helper (auto-starts only required services)
        testHelper = new OptimizedTestHelper(getInfrastructure());
        testHelper.startRequiredServices(this);

        // Validate services are running (fast-fail)
        testHelper.validateServices(
                ServiceRequirement.ServiceType.AUTH,
                ServiceRequirement.ServiceType.CUSTOMER
        );

        // Get clients
        customerClient = testHelper.getClient(ServiceRequirement.ServiceType.CUSTOMER);
        userManager = testHelper.getUserManager();

        // Create test users before tests run
        createTestUsers();

        log.info("✓ Test setup complete");
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
     * Usernames are stored as instance variables so they can be reused across test methods.
     */
    private void createTestUsers() {
        log.info("Creating test users...");

        // Create users with unique names to avoid conflicts
        // Use a single timestamp for all users in this test run
        String timestamp = String.valueOf(System.currentTimeMillis());

        // Store usernames as instance variables
        updateUserUsername = "updateuser_" + timestamp;
        validationUserUsername = "validationuser_" + timestamp;
        phoneValidationUserUsername = "phonevalidationuser_" + timestamp;
        requiredFieldsUserUsername = "requiredfieldsuser_" + timestamp;

        // Create users
        userManager.createUser(updateUserUsername, updateUserUsername + "@test.com");
        userManager.createUser(validationUserUsername, validationUserUsername + "@test.com");
        userManager.createUser(phoneValidationUserUsername, phoneValidationUserUsername + "@test.com");
        userManager.createUser(requiredFieldsUserUsername, requiredFieldsUserUsername + "@test.com");

        log.info("✓ Test users created: {}", timestamp);
    }

    @Test
    @DisplayName("User can update their profile")
    void userCanUpdateProfile() throws Exception {
        // Use pre-created user (username stored in instance variable)
        TestDataValidator.requireUserExists(userManager, updateUserUsername);
        String accessToken = userManager.getToken(updateUserUsername);
        TestDataValidator.requireNotNull(accessToken, "Access token");

        // Wait for profile creation (from event)
        Thread.sleep(2000);

        // Get initial profile
        TestHttpClient.HttpResponse initialProfile = customerClient.get("/api/v1/customers/me", accessToken);
        TestDataValidator.requireSuccess(initialProfile, "Get initial profile");

        Map<String, Object> initial = customerClient.parseJson(initialProfile.getBody());
        log.info("Initial profile: firstName={}", initial.get("firstName"));

        // Update profile
        Map<String, String> updateRequest = new HashMap<>();
        updateRequest.put("firstName", "John");
        updateRequest.put("lastName", "Doe");
        updateRequest.put("phoneNumber", "+254712345678");
        updateRequest.put("email", "john.doe@example.com");
        updateRequest.put("address", "123 Main St, Nairobi");

        TestHttpClient.HttpResponse updateResponse = customerClient.put(
                "/api/v1/customers/me",
                updateRequest,
                accessToken
        );
        assertThat(updateResponse.getStatusCode()).isEqualTo(200);

        Map<String, Object> updated = customerClient.parseJson(updateResponse.getBody());
        assertThat(updated.get("firstName")).isEqualTo("John");
        assertThat(updated.get("lastName")).isEqualTo("Doe");
        assertThat(updated.get("phoneNumber")).isEqualTo("+254712345678");
        assertThat(updated.get("email")).isEqualTo("john.doe@example.com");
        assertThat(updated.get("address")).isEqualTo("123 Main St, Nairobi");
        log.info("✓ Profile updated successfully");

        // Verify changes persisted
        TestHttpClient.HttpResponse verifyResponse = customerClient.get("/api/v1/customers/me", accessToken);
        Map<String, Object> verified = customerClient.parseJson(verifyResponse.getBody());
        assertThat(verified.get("firstName")).isEqualTo("John");
        assertThat(verified.get("lastName")).isEqualTo("Doe");
        log.info("✓ Changes persisted");
    }

    @Test
    @DisplayName("Profile update validates email format")
    void profileUpdateValidatesEmailFormat() throws Exception {
        // Use pre-created user (username stored in instance variable)
        TestDataValidator.requireUserExists(userManager, validationUserUsername);
        String accessToken = userManager.getToken(validationUserUsername);
        TestDataValidator.requireNotNull(accessToken, "Access token");

        Thread.sleep(2000);

        // Try to update with invalid email
        Map<String, String> updateRequest = new HashMap<>();
        updateRequest.put("firstName", "John");
        updateRequest.put("lastName", "Doe");
        updateRequest.put("phoneNumber", "+254712345678");
        updateRequest.put("email", "invalid-email"); // Invalid format
        updateRequest.put("address", "123 Main St");

        TestHttpClient.HttpResponse response = customerClient.put(
                "/api/v1/customers/me",
                updateRequest,
                accessToken
        );

        // Should get 400 Bad Request
        assertThat(response.getStatusCode()).isEqualTo(400);
        log.info("✓ Invalid email rejected with status: {}", response.getStatusCode());
    }

    @Test
    @DisplayName("Profile update validates phone format")
    void profileUpdateValidatesPhoneFormat() throws Exception {
        // Use pre-created user (username stored in instance variable)
        TestDataValidator.requireUserExists(userManager, phoneValidationUserUsername);
        String accessToken = userManager.getToken(phoneValidationUserUsername);
        TestDataValidator.requireNotNull(accessToken, "Access token");

        Thread.sleep(2000);

        // Try to update with invalid phone
        Map<String, String> updateRequest = new HashMap<>();
        updateRequest.put("firstName", "John");
        updateRequest.put("lastName", "Doe");
        updateRequest.put("phoneNumber", "invalid-phone"); // Invalid format
        updateRequest.put("email", "valid@example.com");
        updateRequest.put("address", "123 Main St");

        TestHttpClient.HttpResponse response = customerClient.put(
                "/api/v1/customers/me",
                updateRequest,
                accessToken
        );

        // Should get 400 Bad Request
        assertThat(response.getStatusCode()).isEqualTo(400);
        log.info("✓ Invalid phone rejected with status: {}", response.getStatusCode());
    }

    @Test
    @DisplayName("Profile update requires all fields")
    void profileUpdateRequiresAllFields() throws Exception {
        // Use pre-created user (username stored in instance variable)
        TestDataValidator.requireUserExists(userManager, requiredFieldsUserUsername);
        String accessToken = userManager.getToken(requiredFieldsUserUsername);
        TestDataValidator.requireNotNull(accessToken, "Access token");

        Thread.sleep(2000);

        // Try to update with missing fields
        Map<String, String> updateRequest = new HashMap<>();
        updateRequest.put("firstName", "John");
        // Missing lastName and email (phoneNumber is optional)

        TestHttpClient.HttpResponse response = customerClient.put(
                "/api/v1/customers/me",
                updateRequest,
                accessToken
        );

        // Should get 400 Bad Request
        assertThat(response.getStatusCode()).isEqualTo(400);
        log.info("✓ Missing fields rejected with status: {}", response.getStatusCode());
    }
}

