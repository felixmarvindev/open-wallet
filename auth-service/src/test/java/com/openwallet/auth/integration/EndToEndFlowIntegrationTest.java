package com.openwallet.auth.integration;

import com.openwallet.auth.dto.LoginRequest;
import com.openwallet.auth.dto.LoginResponse;
import com.openwallet.auth.dto.RegisterRequest;
import com.openwallet.auth.dto.RegisterResponse;
import com.openwallet.auth.events.UserEvent;
import com.openwallet.auth.events.UserEventProducer;
import com.openwallet.auth.service.AuthService;
import com.openwallet.auth.service.KeycloakService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

/**
 * Comprehensive end-to-end integration test for the complete user onboarding
 * flow.
 * 
 * Flow tested:
 * 1. Register user → Get userId
 * 2. Login user → Get JWT tokens
 * 3. Verify Kafka events are published (USER_REGISTERED, USER_LOGIN)
 * 
 * This test demonstrates the complete authentication flow that would be used
 * before creating a customer profile.
 * 
 * Note: KeycloakService is mocked. For testing with real Keycloak, use manual
 * testing.
 */
@SpringBootTest
@ActiveProfiles("test")
@Transactional
@SuppressWarnings("null")
class EndToEndFlowIntegrationTest {

    @Autowired
    private AuthService authService;

    @MockBean
    private KeycloakService keycloakService;

    @MockBean
    private UserEventProducer userEventProducer;

    private RegisterRequest registerRequest;
    private LoginRequest loginRequest;

    @BeforeEach
    void setUp() {
        registerRequest = RegisterRequest.builder()
                .username("endtoend-user")
                .email("endtoend@example.com")
                .password("SecurePass123!@#")
                .build();

        loginRequest = LoginRequest.builder()
                .username("endtoend-user")
                .password("SecurePass123!@#")
                .build();
    }

    @Test
    @DisplayName("Complete end-to-end flow: Register → Login → Verify events")
    void completeEndToEndUserOnboardingFlow() {
        // Given: Mock Keycloak responses
        String userId = "keycloak-user-e2e-456";

        KeycloakService.TokenResponse tokenResponse = new KeycloakService.TokenResponse();
        tokenResponse.setAccess_token("e2e-jwt-access-token");
        tokenResponse.setRefresh_token("e2e-jwt-refresh-token");
        tokenResponse.setExpires_in(900);

        when(keycloakService.createUser(anyString(), anyString(), anyString())).thenReturn(userId);
        when(keycloakService.authenticateUser(anyString(), anyString())).thenReturn(tokenResponse);
        when(keycloakService.getUserIdFromUsername(anyString())).thenReturn(userId);

        // ===== STEP 1: REGISTER USER =====
        RegisterResponse registerResponse = authService.register(registerRequest);

        // Verify registration response
        assertThat(registerResponse).isNotNull();
        assertThat(registerResponse.getUserId()).isEqualTo(userId);
        assertThat(registerResponse.getMessage()).contains("successfully");

        // Verify USER_REGISTERED event was published with correct data
        verify(userEventProducer, times(1)).publish(argThat(event -> {
            return "USER_REGISTERED".equals(event.getEventType()) &&
                    userId.equals(event.getUserId()) &&
                    "endtoend-user".equals(event.getUsername()) &&
                    "endtoend@example.com".equals(event.getEmail()) &&
                    event.getTimestamp() != null &&
                    event.getMetadata() != null &&
                    "registration".equals(event.getMetadata().get("action"));
        }));

        // Verify KeycloakService.createUser was called
        verify(keycloakService, times(1)).createUser(
                "endtoend-user",
                "endtoend@example.com",
                "SecurePass123!@#");

        // ===== STEP 2: LOGIN USER =====
        LoginResponse loginResponse = authService.login(loginRequest);

        // Verify login response
        assertThat(loginResponse).isNotNull();
        assertThat(loginResponse.getAccessToken()).isEqualTo("e2e-jwt-access-token");
        assertThat(loginResponse.getRefreshToken()).isEqualTo("e2e-jwt-refresh-token");
        assertThat(loginResponse.getExpiresIn()).isEqualTo(900);

        // Verify USER_LOGIN event was published with correct data
        verify(userEventProducer, times(1)).publish(argThat(event -> {
            return "USER_LOGIN".equals(event.getEventType()) &&
                    userId.equals(event.getUserId()) &&
                    "endtoend-user".equals(event.getUsername()) &&
                    event.getTimestamp() != null &&
                    event.getMetadata() != null &&
                    "login".equals(event.getMetadata().get("action"));
        }));

        // Verify KeycloakService.authenticateUser was called
        verify(keycloakService, times(1)).authenticateUser(
                "endtoend-user",
                "SecurePass123!@#");
        verify(keycloakService, times(1)).getUserIdFromUsername("endtoend-user");

        // ===== VERIFY COMPLETE FLOW =====
        // Total events published: 2 (USER_REGISTERED + USER_LOGIN)
        verify(userEventProducer, times(2)).publish(any(UserEvent.class));

        // Verify userId is consistent across the flow
        assertThat(registerResponse.getUserId()).isEqualTo(userId);
        // In a real scenario, the userId from registration would be used to create
        // customer
    }

    @Test
    @DisplayName("End-to-end flow should handle registration failure gracefully")
    void endToEndFlowShouldHandleRegistrationFailure() {
        // Given: Registration fails (user already exists)
        when(keycloakService.createUser(anyString(), anyString(), anyString()))
                .thenThrow(new RuntimeException("User with username 'endtoend-user' already exists"));

        // When/Then: Registration should fail with appropriate exception
        try {
            authService.register(registerRequest);
            org.junit.jupiter.api.Assertions.fail("Should have thrown UserAlreadyExistsException");
        } catch (com.openwallet.auth.exception.UserAlreadyExistsException e) {
            assertThat(e.getMessage()).contains("already exists");
        }

        // Verify no events were published
        verify(userEventProducer, never()).publish(any());

        // Verify login would also fail (user doesn't exist in our mock)
        when(keycloakService.authenticateUser(anyString(), anyString()))
                .thenThrow(new RuntimeException("Authentication failed: Invalid credentials"));

        try {
            authService.login(loginRequest);
            org.junit.jupiter.api.Assertions.fail("Should have thrown InvalidCredentialsException");
        } catch (com.openwallet.auth.exception.InvalidCredentialsException e) {
            assertThat(e.getMessage()).contains("Invalid username or password");
        }
    }
}
