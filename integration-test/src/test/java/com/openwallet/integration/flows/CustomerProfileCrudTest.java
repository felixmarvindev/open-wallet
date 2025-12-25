package com.openwallet.integration.flows;

import com.openwallet.integration.IntegrationTestBase;
import com.openwallet.integration.infrastructure.ServiceContainerManager;
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
 * Tests customer profile CRUD operations:
 * - Update profile (name, email, phone)
 * - Validate profile updates
 * - Cannot update to existing email
 */
@Slf4j
@DisplayName("Customer Profile CRUD Operations")
public class CustomerProfileCrudTest extends IntegrationTestBase {

    private ServiceContainerManager serviceManager;
    private TestHttpClient authClient;
    private TestHttpClient customerClient;

    @BeforeEach
    void setUp() {
        log.info("Starting services for customer profile CRUD test...");
        serviceManager = new ServiceContainerManager(getInfrastructure());
        serviceManager.startAll();

        authClient = new TestHttpClient(serviceManager.getAuthService().getBaseUrl());
        customerClient = new TestHttpClient(serviceManager.getCustomerService().getBaseUrl());

        log.info("✓ Services ready");
    }

    @AfterEach
    void tearDown() {
        if (serviceManager != null) serviceManager.stopAll();
    }

    @Test
    @DisplayName("User can update their profile")
    void userCanUpdateProfile() throws Exception {
        String[] credentials = registerAndLogin("updateuser");
        String accessToken = credentials[0];

        Thread.sleep(2000); // Wait for profile creation

        // Get initial profile
        TestHttpClient.HttpResponse initialProfile = customerClient.get("/api/v1/customers/me", accessToken);
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
        String[] credentials = registerAndLogin("validationuser");
        String accessToken = credentials[0];

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
        String[] credentials = registerAndLogin("phonevalidationuser");
        String accessToken = credentials[0];

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
        String[] credentials = registerAndLogin("requiredfieldsuser");
        String accessToken = credentials[0];

        Thread.sleep(2000);

        // Try to update with missing fields
        Map<String, String> updateRequest = new HashMap<>();
        updateRequest.put("firstName", "John");
        // Missing lastName, phoneNumber, email

        TestHttpClient.HttpResponse response = customerClient.put(
                "/api/v1/customers/me",
                updateRequest,
                accessToken
        );

        // Should get 400 Bad Request
        assertThat(response.getStatusCode()).isEqualTo(400);
        log.info("✓ Missing fields rejected with status: {}", response.getStatusCode());
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



