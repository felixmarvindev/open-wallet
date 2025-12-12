package com.openwallet.auth.integration;

import com.openwallet.auth.dto.LoginRequest;
import com.openwallet.auth.dto.LoginResponse;
import com.openwallet.auth.dto.RegisterRequest;
import com.openwallet.auth.dto.RegisterResponse;
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
 * End-to-end integration test for the complete user registration and authentication flow.
 * Tests: Register → Login → Verify events published.
 * 
 * Note: This test mocks KeycloakService to avoid requiring a real Keycloak instance.
 * For full integration testing with real Keycloak, see manual testing guide.
 */
@SpringBootTest
@ActiveProfiles("test")
@Transactional
@SuppressWarnings("null")
class AuthToCustomerFlowIntegrationTest {

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
                .username("e2e-test-user")
                .email("e2e.test@example.com")
                .password("Test123!@#")
                .build();

        loginRequest = LoginRequest.builder()
                .username("e2e-test-user")
                .password("Test123!@#")
                .build();
    }

    @Test
    @DisplayName("Complete flow: Register → Login → Verify events published")
    void completeUserRegistrationAndLoginFlow() {
        // Given: Mock Keycloak responses
        String userId = "keycloak-user-e2e-123";
        KeycloakService.TokenResponse tokenResponse = new KeycloakService.TokenResponse();
        tokenResponse.setAccess_token("e2e-access-token-123");
        tokenResponse.setRefresh_token("e2e-refresh-token-123");
        tokenResponse.setExpires_in(900);

        when(keycloakService.createUser(anyString(), anyString(), anyString())).thenReturn(userId);
        when(keycloakService.authenticateUser(anyString(), anyString())).thenReturn(tokenResponse);
        when(keycloakService.getUserIdFromUsername(anyString())).thenReturn(userId);

        // Step 1: Register user
        RegisterResponse registerResponse = authService.register(registerRequest);
        
        // Verify registration
        assertThat(registerResponse).isNotNull();
        assertThat(registerResponse.getUserId()).isEqualTo(userId);
        assertThat(registerResponse.getMessage()).contains("successfully");

        // Verify USER_REGISTERED event was published
        verify(userEventProducer, times(1)).publish(argThat(event -> 
            "USER_REGISTERED".equals(event.getEventType()) &&
            userId.equals(event.getUserId()) &&
            "e2e-test-user".equals(event.getUsername()) &&
            "e2e.test@example.com".equals(event.getEmail())
        ));

        // Step 2: Login user
        LoginResponse loginResponse = authService.login(loginRequest);

        // Verify login
        assertThat(loginResponse).isNotNull();
        assertThat(loginResponse.getAccessToken()).isEqualTo("e2e-access-token-123");
        assertThat(loginResponse.getRefreshToken()).isEqualTo("e2e-refresh-token-123");
        assertThat(loginResponse.getExpiresIn()).isEqualTo(900);

        // Verify USER_LOGIN event was published
        verify(userEventProducer, times(1)).publish(argThat(event -> 
            "USER_LOGIN".equals(event.getEventType()) &&
            userId.equals(event.getUserId()) &&
            "e2e-test-user".equals(event.getUsername())
        ));

        // Verify KeycloakService was called correctly
        verify(keycloakService, times(1)).createUser(
                "e2e-test-user",
                "e2e.test@example.com",
                "Test123!@#"
        );
        verify(keycloakService, times(1)).authenticateUser(
                "e2e-test-user",
                "Test123!@#"
        );
        verify(keycloakService, times(1)).getUserIdFromUsername("e2e-test-user");
    }

    @Test
    @DisplayName("Register should fail when user already exists")
    void registerShouldFailWhenUserExists() {
        // Given: User already exists in Keycloak
        when(keycloakService.createUser(anyString(), anyString(), anyString()))
                .thenThrow(new RuntimeException("User with username 'e2e-test-user' already exists"));

        // When/Then: Registration should fail
        try {
            authService.register(registerRequest);
            org.junit.jupiter.api.Assertions.fail("Should have thrown UserAlreadyExistsException");
        } catch (com.openwallet.auth.exception.UserAlreadyExistsException e) {
            assertThat(e.getMessage()).contains("already exists");
        }

        // Verify no event was published
        verify(userEventProducer, never()).publish(any());
    }

    @Test
    @DisplayName("Login should fail with invalid credentials")
    void loginShouldFailWithInvalidCredentials() {
        // Given: Invalid credentials
        when(keycloakService.authenticateUser(anyString(), anyString()))
                .thenThrow(new RuntimeException("Authentication failed: Invalid credentials"));

        // When/Then: Login should fail
        try {
            authService.login(loginRequest);
            org.junit.jupiter.api.Assertions.fail("Should have thrown InvalidCredentialsException");
        } catch (com.openwallet.auth.exception.InvalidCredentialsException e) {
            assertThat(e.getMessage()).contains("Invalid username or password");
        }

        // Verify no event was published
        verify(userEventProducer, never()).publish(any());
    }
}

