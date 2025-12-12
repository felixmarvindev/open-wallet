package com.openwallet.auth.service;

import com.openwallet.auth.dto.LoginRequest;
import com.openwallet.auth.dto.LogoutRequest;
import com.openwallet.auth.dto.RegisterRequest;
import com.openwallet.auth.events.UserEvent;
import com.openwallet.auth.events.UserEventProducer;
import com.openwallet.auth.service.KeycloakService.TokenResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

/**
 * Tests for Kafka event publishing in AuthService.
 */
@ExtendWith(MockitoExtension.class)
@SuppressWarnings("null")
class AuthServiceEventTest {

    @Mock
    private KeycloakService keycloakService;

    @Mock
    private UserEventProducer userEventProducer;

    @InjectMocks
    private AuthService authService;

    private RegisterRequest registerRequest;
    private LoginRequest loginRequest;
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

        logoutRequest = LogoutRequest.builder()
                .refreshToken("refresh-token-123")
                .build();
    }

    @Test
    @DisplayName("Register should publish USER_REGISTERED event with correct data")
    void registerShouldPublishUserRegisteredEvent() {
        // Given
        String userId = "keycloak-user-id-123";
        when(keycloakService.createUser(anyString(), anyString(), anyString())).thenReturn(userId);

        // When
        authService.register(registerRequest);

        // Then
        ArgumentCaptor<UserEvent> eventCaptor = ArgumentCaptor.forClass(UserEvent.class);
        verify(userEventProducer, times(1)).publish(eventCaptor.capture());

        UserEvent event = eventCaptor.getValue();
        assertThat(event.getEventType()).isEqualTo("USER_REGISTERED");
        assertThat(event.getUserId()).isEqualTo(userId);
        assertThat(event.getUsername()).isEqualTo("testuser");
        assertThat(event.getEmail()).isEqualTo("test@example.com");
        assertThat(event.getTimestamp()).isNotNull();
        assertThat(event.getMetadata()).isNotNull();
        assertThat(event.getMetadata().get("action")).isEqualTo("registration");
    }

    @Test
    @DisplayName("Login should publish USER_LOGIN event with correct data")
    void loginShouldPublishUserLoginEvent() {
        // Given
        String userId = "keycloak-user-id-123";
        TokenResponse tokenResponse = new TokenResponse();
        tokenResponse.setAccess_token("access-token-123");
        tokenResponse.setRefresh_token("refresh-token-123");
        tokenResponse.setExpires_in(900);

        when(keycloakService.authenticateUser(anyString(), anyString())).thenReturn(tokenResponse);
        when(keycloakService.getUserIdFromUsername(anyString())).thenReturn(userId);

        // When
        authService.login(loginRequest);

        // Then
        ArgumentCaptor<UserEvent> eventCaptor = ArgumentCaptor.forClass(UserEvent.class);
        verify(userEventProducer, times(1)).publish(eventCaptor.capture());

        UserEvent event = eventCaptor.getValue();
        assertThat(event.getEventType()).isEqualTo("USER_LOGIN");
        assertThat(event.getUserId()).isEqualTo(userId);
        assertThat(event.getUsername()).isEqualTo("testuser");
        assertThat(event.getTimestamp()).isNotNull();
        assertThat(event.getMetadata()).isNotNull();
        assertThat(event.getMetadata().get("action")).isEqualTo("login");
    }

    @Test
    @DisplayName("Logout should publish USER_LOGOUT event with correct data")
    void logoutShouldPublishUserLogoutEvent() {
        // Given
        String userId = "keycloak-user-id-123";
        doNothing().when(keycloakService).logoutUser(anyString());

        // When
        authService.logout(logoutRequest, userId);

        // Then
        ArgumentCaptor<UserEvent> eventCaptor = ArgumentCaptor.forClass(UserEvent.class);
        verify(userEventProducer, times(1)).publish(eventCaptor.capture());

        UserEvent event = eventCaptor.getValue();
        assertThat(event.getEventType()).isEqualTo("USER_LOGOUT");
        assertThat(event.getUserId()).isEqualTo(userId);
        assertThat(event.getTimestamp()).isNotNull();
        assertThat(event.getMetadata()).isNotNull();
        assertThat(event.getMetadata().get("action")).isEqualTo("logout");
    }

    @Test
    @DisplayName("Register should not publish event when user creation fails")
    void registerShouldNotPublishEventWhenCreationFails() {
        // Given
        when(keycloakService.createUser(anyString(), anyString(), anyString()))
                .thenThrow(new RuntimeException("User creation failed"));

        // When/Then
        try {
            authService.register(registerRequest);
        } catch (Exception e) {
            // Expected
        }

        // Verify no event was published
        verify(userEventProducer, never()).publish(any());
    }

    @Test
    @DisplayName("Login should not publish event when authentication fails")
    void loginShouldNotPublishEventWhenAuthenticationFails() {
        // Given
        when(keycloakService.authenticateUser(anyString(), anyString()))
                .thenThrow(new RuntimeException("Authentication failed"));

        // When/Then
        try {
            authService.login(loginRequest);
        } catch (Exception e) {
            // Expected
        }

        // Verify no event was published
        verify(userEventProducer, never()).publish(any());
    }

    @Test
    @DisplayName("Refresh should not publish any event")
    void refreshShouldNotPublishEvent() {
        // Given
        TokenResponse tokenResponse = new TokenResponse();
        tokenResponse.setAccess_token("new-access-token");
        tokenResponse.setExpires_in(900);

        when(keycloakService.refreshToken(anyString())).thenReturn(tokenResponse);

        // When
        authService.refresh(com.openwallet.auth.dto.RefreshRequest.builder()
                .refreshToken("refresh-token-123")
                .build());

        // Then
        verify(userEventProducer, never()).publish(any());
    }
}

