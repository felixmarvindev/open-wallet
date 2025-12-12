package com.openwallet.auth.service;

import com.openwallet.auth.dto.*;
import com.openwallet.auth.events.UserEvent;
import com.openwallet.auth.events.UserEventProducer;
import com.openwallet.auth.exception.InvalidCredentialsException;
import com.openwallet.auth.exception.UserAlreadyExistsException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

/**
 * Unit tests for AuthService.
 */
@ExtendWith(MockitoExtension.class)
@SuppressWarnings("null")
class AuthServiceTest {

    @Mock
    private KeycloakService keycloakService;

    @Mock
    private UserEventProducer userEventProducer;

    @InjectMocks
    private AuthService authService;

    private RegisterRequest registerRequest;
    private LoginRequest loginRequest;
    private RefreshRequest refreshRequest;
    private LogoutRequest logoutRequest;

    @BeforeEach
    void setUp() {
        registerRequest = RegisterRequest.builder()
                .username("testuser")
                .email("test@example.com")
                .password("Test123!@#")
                .build();

        loginRequest = LoginRequest.builder()
                .username("testuser")
                .password("Test123!@#")
                .build();

        refreshRequest = RefreshRequest.builder()
                .refreshToken("refresh-token-123")
                .build();

        logoutRequest = LogoutRequest.builder()
                .refreshToken("refresh-token-123")
                .build();
    }

    @Test
    @DisplayName("Register should create user and publish USER_REGISTERED event")
    void registerShouldCreateUserAndPublishEvent() {
        // Given
        String userId = "keycloak-user-id-123";
        when(keycloakService.createUser(anyString(), anyString(), anyString())).thenReturn(userId);

        // When
        RegisterResponse response = authService.register(registerRequest);

        // Then
        assertThat(response).isNotNull();
        assertThat(response.getUserId()).isEqualTo(userId);
        assertThat(response.getMessage()).contains("successfully");

        // Verify KeycloakService was called
        verify(keycloakService, times(1)).createUser(
                registerRequest.getUsername(),
                registerRequest.getEmail(),
                registerRequest.getPassword());

        // Verify event was published
        ArgumentCaptor<UserEvent> eventCaptor = ArgumentCaptor.forClass(UserEvent.class);
        verify(userEventProducer, times(1)).publish(eventCaptor.capture());

        UserEvent publishedEvent = eventCaptor.getValue();
        assertThat(publishedEvent.getUserId()).isEqualTo(userId);
        assertThat(publishedEvent.getEventType()).isEqualTo("USER_REGISTERED");
        assertThat(publishedEvent.getUsername()).isEqualTo(registerRequest.getUsername());
        assertThat(publishedEvent.getEmail()).isEqualTo(registerRequest.getEmail());
    }

    @Test
    @DisplayName("Register should throw UserAlreadyExistsException when user exists")
    void registerShouldThrowExceptionWhenUserExists() {
        // Given
        when(keycloakService.createUser(anyString(), anyString(), anyString()))
                .thenThrow(new RuntimeException("User with username 'testuser' already exists"));

        // When/Then
        assertThatThrownBy(() -> authService.register(registerRequest))
                .isInstanceOf(UserAlreadyExistsException.class)
                .hasMessageContaining("already exists");

        verify(userEventProducer, never()).publish(any());
    }

    @Test
    @DisplayName("Login should authenticate user and publish USER_LOGIN event")
    void loginShouldAuthenticateAndPublishEvent() {
        // Given
        String userId = "keycloak-user-id-123";
        KeycloakService.TokenResponse tokenResponse = new KeycloakService.TokenResponse();
        tokenResponse.setAccess_token("access-token-123");
        tokenResponse.setRefresh_token("refresh-token-123");
        tokenResponse.setExpires_in(900);

        when(keycloakService.authenticateUser(anyString(), anyString())).thenReturn(tokenResponse);
        when(keycloakService.getUserIdFromUsername(anyString())).thenReturn(userId);

        // When
        LoginResponse response = authService.login(loginRequest);

        // Then
        assertThat(response).isNotNull();
        assertThat(response.getAccessToken()).isEqualTo("access-token-123");
        assertThat(response.getRefreshToken()).isEqualTo("refresh-token-123");
        assertThat(response.getExpiresIn()).isEqualTo(900);

        // Verify KeycloakService was called
        verify(keycloakService, times(1)).authenticateUser(
                loginRequest.getUsername(),
                loginRequest.getPassword());
        verify(keycloakService, times(1)).getUserIdFromUsername(loginRequest.getUsername());

        // Verify event was published
        ArgumentCaptor<UserEvent> eventCaptor = ArgumentCaptor.forClass(UserEvent.class);
        verify(userEventProducer, times(1)).publish(eventCaptor.capture());

        UserEvent publishedEvent = eventCaptor.getValue();
        assertThat(publishedEvent.getUserId()).isEqualTo(userId);
        assertThat(publishedEvent.getEventType()).isEqualTo("USER_LOGIN");
        assertThat(publishedEvent.getUsername()).isEqualTo(loginRequest.getUsername());
    }

    @Test
    @DisplayName("Login should throw InvalidCredentialsException when credentials are invalid")
    void loginShouldThrowExceptionWhenCredentialsInvalid() {
        // Given
        when(keycloakService.authenticateUser(anyString(), anyString()))
                .thenThrow(new RuntimeException("Authentication failed: Invalid credentials"));

        // When/Then
        assertThatThrownBy(() -> authService.login(loginRequest))
                .isInstanceOf(InvalidCredentialsException.class)
                .hasMessageContaining("Invalid username or password");

        verify(userEventProducer, never()).publish(any());
    }

    @Test
    @DisplayName("Refresh should return new access token")
    void refreshShouldReturnNewToken() {
        // Given
        KeycloakService.TokenResponse tokenResponse = new KeycloakService.TokenResponse();
        tokenResponse.setAccess_token("new-access-token-456");
        tokenResponse.setExpires_in(900);

        when(keycloakService.refreshToken(anyString())).thenReturn(tokenResponse);

        // When
        RefreshResponse response = authService.refresh(refreshRequest);

        // Then
        assertThat(response).isNotNull();
        assertThat(response.getAccessToken()).isEqualTo("new-access-token-456");
        assertThat(response.getExpiresIn()).isEqualTo(900);

        verify(keycloakService, times(1)).refreshToken(refreshRequest.getRefreshToken());
        verify(userEventProducer, never()).publish(any());
    }

    @Test
    @DisplayName("Refresh should throw exception when refresh token is invalid")
    void refreshShouldThrowExceptionWhenTokenInvalid() {
        // Given
        when(keycloakService.refreshToken(anyString()))
                .thenThrow(new RuntimeException("Token refresh failed: Invalid refresh token"));

        // When/Then
        assertThatThrownBy(() -> authService.refresh(refreshRequest))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("Token refresh failed");
    }

    @Test
    @DisplayName("Logout should publish USER_LOGOUT event")
    void logoutShouldPublishEvent() {
        // Given
        String userId = "keycloak-user-id-123";
        doNothing().when(keycloakService).logoutUser(anyString());

        // When
        LogoutResponse response = authService.logout(logoutRequest, userId);

        // Then
        assertThat(response).isNotNull();
        assertThat(response.getMessage()).contains("successfully");

        verify(keycloakService, times(1)).logoutUser(userId);

        // Verify event was published
        ArgumentCaptor<UserEvent> eventCaptor = ArgumentCaptor.forClass(UserEvent.class);
        verify(userEventProducer, times(1)).publish(eventCaptor.capture());

        UserEvent publishedEvent = eventCaptor.getValue();
        assertThat(publishedEvent.getUserId()).isEqualTo(userId);
        assertThat(publishedEvent.getEventType()).isEqualTo("USER_LOGOUT");
    }

    @Test
    @DisplayName("Logout should not throw exception when logout fails")
    void logoutShouldNotThrowExceptionWhenFails() {
        // Given
        String userId = "keycloak-user-id-123";
        doThrow(new RuntimeException("Logout failed")).when(keycloakService).logoutUser(anyString());

        // When
        LogoutResponse response = authService.logout(logoutRequest, userId);

        // Then - should still return success message (best effort)
        assertThat(response).isNotNull();
        assertThat(response.getMessage()).isNotNull();

        // Event should still be published
        verify(userEventProducer, times(1)).publish(any());
    }
}
