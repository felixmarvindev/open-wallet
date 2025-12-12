package com.openwallet.auth.service;

import jakarta.ws.rs.core.Response;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.keycloak.admin.client.Keycloak;
import org.keycloak.admin.client.resource.RealmResource;
import org.keycloak.admin.client.resource.UserResource;
import org.keycloak.admin.client.resource.UsersResource;
import org.keycloak.representations.idm.CredentialRepresentation;
import org.keycloak.representations.idm.RoleRepresentation;
import org.keycloak.representations.idm.UserRepresentation;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestTemplate;

import java.util.Collections;
import java.util.List;

/**
 * Service for interacting with Keycloak Admin API and Token Endpoint.
 * Handles user creation, authentication, token refresh, and logout operations.
 */
@Service
@RequiredArgsConstructor
@Slf4j
@SuppressWarnings("null")
public class KeycloakService {

    private final Keycloak keycloakAdminClient;

    @Value("${keycloak.server-url}")
    private String serverUrl;

    @Value("${keycloak.realm}")
    private String realm;

    @Value("${keycloak.client-id}")
    private String clientId;

    @Value("${keycloak.client-secret}")
    private String clientSecret;

    private final RestTemplate restTemplate;

    /**
     * Creates a new user in Keycloak realm.
     *
     * @param username Username for the new user
     * @param email Email address for the new user
     * @param password Password for the new user
     * @return Keycloak user ID
     * @throws RuntimeException if user creation fails or user already exists
     */
    public String createUser(String username, String email, String password) {
        RealmResource realmResource = keycloakAdminClient.realm(realm);
        UsersResource usersResource = realmResource.users();

        // Check if user already exists
        List<UserRepresentation> existingUsers = usersResource.search(username, true);
        if (!existingUsers.isEmpty()) {
            throw new RuntimeException("User with username '" + username + "' already exists");
        }

        // Check if email already exists
        List<UserRepresentation> existingUsersByEmail = usersResource.searchByEmail(email, true);
        if (!existingUsersByEmail.isEmpty()) {
            throw new RuntimeException("User with email '" + email + "' already exists");
        }

        // Create user representation
        UserRepresentation user = new UserRepresentation();
        user.setUsername(username);
        user.setEmail(email);
        user.setEmailVerified(true);
        user.setEnabled(true);

        // Create user
        Response response = usersResource.create(user);
        
        if (response.getStatus() != Response.Status.CREATED.getStatusCode()) {
            String errorMessage = "Failed to create user in Keycloak. Status: " + response.getStatus();
            log.error(errorMessage);
            throw new RuntimeException(errorMessage);
        }

        // Get the created user ID from location header
        String userId = getCreatedId(response);
        log.info("Created user in Keycloak: userId={}, username={}", userId, username);

        // Set password
        UserResource userResource = usersResource.get(userId);
        CredentialRepresentation credential = new CredentialRepresentation();
        credential.setType(CredentialRepresentation.PASSWORD);
        credential.setValue(password);
        credential.setTemporary(false);
        userResource.resetPassword(credential);

        // Assign default USER role
        assignDefaultRole(userResource);

        return userId;
    }

    /**
     * Authenticates a user and returns JWT tokens.
     *
     * @param username Username
     * @param password Password
     * @return TokenResponse containing access token, refresh token, and expiration
     * @throws RuntimeException if authentication fails
     */
    public TokenResponse authenticateUser(String username, String password) {
        String tokenUrl = serverUrl + "/realms/" + realm + "/protocol/openid-connect/token";

        MultiValueMap<String, String> params = new LinkedMultiValueMap<>();
        params.add("grant_type", "password");
        params.add("client_id", clientId);
        params.add("client_secret", clientSecret);
        params.add("username", username);
        params.add("password", password);

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_FORM_URLENCODED);

        HttpEntity<MultiValueMap<String, String>> request = new HttpEntity<>(params, headers);

        try {
            ResponseEntity<TokenResponse> response = restTemplate.postForEntity(
                    tokenUrl, request, TokenResponse.class);

            if (response.getStatusCode().is2xxSuccessful() && response.getBody() != null) {
                log.info("User authenticated successfully: username={}", username);
                return response.getBody();
            } else {
                throw new RuntimeException("Authentication failed: Invalid credentials");
            }
        } catch (org.springframework.web.client.HttpClientErrorException e) {
            if (e.getStatusCode() == HttpStatus.UNAUTHORIZED) {
                throw new RuntimeException("Authentication failed: Invalid credentials", e);
            }
            throw new RuntimeException("Authentication failed: " + e.getMessage(), e);
        }
    }

    /**
     * Refreshes an access token using a refresh token.
     *
     * @param refreshToken Refresh token
     * @return TokenResponse containing new access token and refresh token
     * @throws RuntimeException if token refresh fails
     */
    public TokenResponse refreshToken(String refreshToken) {
        String tokenUrl = serverUrl + "/realms/" + realm + "/protocol/openid-connect/token";

        MultiValueMap<String, String> params = new LinkedMultiValueMap<>();
        params.add("grant_type", "refresh_token");
        params.add("client_id", clientId);
        params.add("client_secret", clientSecret);
        params.add("refresh_token", refreshToken);

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_FORM_URLENCODED);

        HttpEntity<MultiValueMap<String, String>> request = new HttpEntity<>(params, headers);

        try {
            ResponseEntity<TokenResponse> response = restTemplate.postForEntity(
                    tokenUrl, request, TokenResponse.class);

            if (response.getStatusCode().is2xxSuccessful() && response.getBody() != null) {
                log.info("Token refreshed successfully");
                return response.getBody();
            } else {
                throw new RuntimeException("Token refresh failed");
            }
        } catch (org.springframework.web.client.HttpClientErrorException e) {
            if (e.getStatusCode() == HttpStatus.UNAUTHORIZED) {
                throw new RuntimeException("Token refresh failed: Invalid refresh token", e);
            }
            throw new RuntimeException("Token refresh failed: " + e.getMessage(), e);
        }
    }

    /**
     * Logs out a user by invalidating their session.
     *
     * @param userId Keycloak user ID
     */
    public void logoutUser(String userId) {
        try {
            RealmResource realmResource = keycloakAdminClient.realm(realm);
            UserResource userResource = realmResource.users().get(userId);
            userResource.logout();
            log.info("User logged out: userId={}", userId);
        } catch (Exception e) {
            log.warn("Failed to logout user: userId={}, error={}", userId, e.getMessage());
            // Don't throw exception - logout is best effort
        }
    }

    /**
     * Assigns the default USER role to a newly created user.
     *
     * @param userResource User resource for the created user
     */
    private void assignDefaultRole(UserResource userResource) {
        try {
            RealmResource realmResource = keycloakAdminClient.realm(realm);
            RoleRepresentation userRole = realmResource.roles().get("USER").toRepresentation();
            userResource.roles().realmLevel().add(Collections.singletonList(userRole));
            log.debug("Assigned USER role to new user");
        } catch (Exception e) {
            log.warn("Failed to assign USER role to new user: {}", e.getMessage());
            // Don't throw exception - role assignment can be done manually if needed
        }
    }

    /**
     * Gets userId from username by querying Keycloak.
     *
     * @param username Username
     * @return Keycloak user ID
     * @throws RuntimeException if user not found
     */
    public String getUserIdFromUsername(String username) {
        RealmResource realmResource = keycloakAdminClient.realm(realm);
        UsersResource usersResource = realmResource.users();
        
        List<UserRepresentation> users = usersResource.search(username, true);
        if (users.isEmpty()) {
            throw new RuntimeException("User not found: " + username);
        }
        
        return users.get(0).getId();
    }

    /**
     * Extracts the created resource ID from the response location header.
     *
     * @param response Keycloak response
     * @return Resource ID
     */
    private String getCreatedId(Response response) {
        String location = response.getLocation().getPath();
        return location.substring(location.lastIndexOf('/') + 1);
    }

    /**
     * Token response DTO for Keycloak token endpoint responses.
     */
    @lombok.Data
    public static class TokenResponse {
        private String access_token;
        private String refresh_token;
        private String token_type;
        private Integer expires_in;
        private Integer refresh_expires_in;
        private String scope;
    }
}

