package com.openwallet.auth.service;

import com.openwallet.auth.dto.*;
import com.openwallet.auth.events.UserEvent;
import com.openwallet.auth.events.UserEventProducer;
import com.openwallet.auth.service.KeycloakService.TokenResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;

/**
 * Service for handling authentication and user management operations.
 * Delegates to KeycloakService for Keycloak interactions and publishes events to Kafka.
 */
@Service
@RequiredArgsConstructor
@Slf4j
@SuppressWarnings("null")
public class AuthService {

    private final KeycloakService keycloakService;
    private final UserEventProducer userEventProducer;

    /**
     * Registers a new user in Keycloak and publishes USER_REGISTERED event.
     *
     * @param request Registration request
     * @return Registration response with userId
     * @throws RuntimeException if user already exists or registration fails
     */
    public RegisterResponse register(RegisterRequest request) {
        log.info("Registering new user: username={}, email={}", request.getUsername(), request.getEmail());

        try {
            // Create user in Keycloak
            String userId = keycloakService.createUser(
                    request.getUsername(),
                    request.getEmail(),
                    request.getPassword()
            );

            // Publish USER_REGISTERED event
            UserEvent event = UserEvent.builder()
                    .userId(userId)
                    .eventType("USER_REGISTERED")
                    .username(request.getUsername())
                    .email(request.getEmail())
                    .timestamp(LocalDateTime.now())
                    .metadata(createMetadata("registration", request.getUsername(), request.getEmail()))
                    .build();
            userEventProducer.publish(event);

            log.info("User registered successfully: userId={}, username={}", userId, request.getUsername());

            return RegisterResponse.builder()
                    .userId(userId)
                    .message("User registered successfully")
                    .build();
        } catch (RuntimeException e) {
            log.error("User registration failed: username={}, error={}", request.getUsername(), e.getMessage());
            // Map specific errors to custom exceptions
            if (e.getMessage() != null && e.getMessage().contains("already exists")) {
                throw new com.openwallet.auth.exception.UserAlreadyExistsException(e.getMessage());
            }
            throw e; // Re-throw to be handled by exception handler
        }
    }

    /**
     * Authenticates a user and returns JWT tokens. Publishes USER_LOGIN event.
     *
     * @param request Login request
     * @return Login response with access token, refresh token, and expiration
     * @throws RuntimeException if authentication fails
     */
    public LoginResponse login(LoginRequest request) {
        log.info("User login attempt: username={}", request.getUsername());

        try {
            // Authenticate user via Keycloak
            TokenResponse tokenResponse = keycloakService.authenticateUser(
                    request.getUsername(),
                    request.getPassword()
            );

            // Get userId from Keycloak using username
            String userId = keycloakService.getUserIdFromUsername(request.getUsername());

            // Publish USER_LOGIN event
            UserEvent event = UserEvent.builder()
                    .userId(userId)
                    .eventType("USER_LOGIN")
                    .username(request.getUsername())
                    .timestamp(LocalDateTime.now())
                    .metadata(createMetadata("login", request.getUsername(), null))
                    .build();
            userEventProducer.publish(event);

            log.info("User logged in successfully: username={}", request.getUsername());

            return LoginResponse.builder()
                    .accessToken(tokenResponse.getAccess_token())
                    .refreshToken(tokenResponse.getRefresh_token())
                    .expiresIn(tokenResponse.getExpires_in() != null ? tokenResponse.getExpires_in() : 900) // Default 15 min
                    .build();
        } catch (RuntimeException e) {
            log.error("User login failed: username={}, error={}", request.getUsername(), e.getMessage());
            // Map authentication errors to custom exception
            if (e.getMessage() != null && (e.getMessage().contains("Invalid credentials") || 
                    e.getMessage().contains("Authentication failed"))) {
                throw new com.openwallet.auth.exception.InvalidCredentialsException("Invalid username or password");
            }
            throw e; // Re-throw to be handled by exception handler
        }
    }

    /**
     * Refreshes an access token using a refresh token.
     *
     * @param request Refresh request with refresh token
     * @return Refresh response with new access token and expiration
     * @throws RuntimeException if token refresh fails
     */
    public RefreshResponse refresh(RefreshRequest request) {
        log.info("Token refresh attempt");

        try {
            // Refresh token via Keycloak
            TokenResponse tokenResponse = keycloakService.refreshToken(request.getRefreshToken());

            log.info("Token refreshed successfully");

            return RefreshResponse.builder()
                    .accessToken(tokenResponse.getAccess_token())
                    .expiresIn(tokenResponse.getExpires_in() != null ? tokenResponse.getExpires_in() : 900)
                    .build();
        } catch (RuntimeException e) {
            log.error("Token refresh failed: error={}", e.getMessage());
            throw e; // Re-throw to be handled by exception handler
        }
    }

    /**
     * Logs out a user and publishes USER_LOGOUT event.
     * Note: In a production system, you would decode the refresh token to get userId.
     * For now, this is a simplified implementation that accepts userId as a parameter.
     *
     * @param request Logout request with refresh token
     * @param userId User ID (extracted from JWT in controller)
     * @return Logout response
     */
    public LogoutResponse logout(LogoutRequest request, String userId) {
        log.info("User logout attempt: userId={}", userId);

        try {
            if (userId != null) {
                // Logout user in Keycloak
                keycloakService.logoutUser(userId);

                // Publish USER_LOGOUT event
                UserEvent event = UserEvent.builder()
                        .userId(userId)
                        .eventType("USER_LOGOUT")
                        .timestamp(LocalDateTime.now())
                        .metadata(createMetadata("logout", null, null))
                        .build();
                userEventProducer.publish(event);
            }

            log.info("User logged out successfully: userId={}", userId);

            return LogoutResponse.builder()
                    .message("User logged out successfully")
                    .build();
        } catch (Exception e) {
            log.warn("User logout failed: userId={}, error={}", userId, e.getMessage());
            // Don't throw exception - logout is best effort
            return LogoutResponse.builder()
                    .message("Logout completed")
                    .build();
        }
    }


    /**
     * Creates metadata map for events.
     */
    private Map<String, Object> createMetadata(String action, String username, String email) {
        Map<String, Object> metadata = new HashMap<>();
        metadata.put("action", action);
        if (username != null) {
            metadata.put("username", username);
        }
        if (email != null) {
            metadata.put("email", email);
        }
        return metadata;
    }
}

