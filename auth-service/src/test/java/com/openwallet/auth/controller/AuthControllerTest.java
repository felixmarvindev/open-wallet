package com.openwallet.auth.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.openwallet.auth.dto.*;
import com.openwallet.auth.service.AuthService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Integration tests for AuthController.
 */
@WebMvcTest(AuthController.class)
@AutoConfigureMockMvc(addFilters = false)
@ActiveProfiles("test")
@SuppressWarnings("null")
class AuthControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private AuthService authService;

    @Test
    @DisplayName("POST /register should return 201 with user details")
    void registerShouldReturnSuccess() throws Exception {
        // Given
        RegisterRequest request = RegisterRequest.builder()
                .username("testuser")
                .email("test@example.com")
                .password("Test123!@#")
                .build();

        RegisterResponse response = RegisterResponse.builder()
                .userId("keycloak-user-id-123")
                .username("testuser")
                .email("test@example.com")
                .message("User registered successfully")
                .build();

        when(authService.register(any(RegisterRequest.class))).thenReturn(response);

        // When/Then
        mockMvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.userId").value("keycloak-user-id-123"))
                .andExpect(jsonPath("$.username").value("testuser"))
                .andExpect(jsonPath("$.email").value("test@example.com"))
                .andExpect(jsonPath("$.message").value("User registered successfully"));
    }

    @Test
    @DisplayName("POST /register should return 400 when validation fails")
    void registerShouldReturn400WhenValidationFails() throws Exception {
        // Given - Invalid request (missing email)
        RegisterRequest request = RegisterRequest.builder()
                .username("testuser")
                .email("") // Invalid email
                .password("short") // Too short
                .build();

        // When/Then
        mockMvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("POST /login should return 200 with tokens")
    void loginShouldReturnSuccess() throws Exception {
        // Given
        LoginRequest request = LoginRequest.builder()
                .username("testuser")
                .password("Test123!@#")
                .build();

        LoginResponse response = LoginResponse.builder()
                .accessToken("access-token-123")
                .refreshToken("refresh-token-123")
                .expiresIn(900)
                .build();

        when(authService.login(any(LoginRequest.class))).thenReturn(response);

        // When/Then
        mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accessToken").value("access-token-123"))
                .andExpect(jsonPath("$.refreshToken").value("refresh-token-123"))
                .andExpect(jsonPath("$.expiresIn").value(900));
    }

    @Test
    @DisplayName("POST /login should return 400 when validation fails")
    void loginShouldReturn400WhenValidationFails() throws Exception {
        // Given - Invalid request (missing password)
        LoginRequest request = LoginRequest.builder()
                .username("testuser")
                .password("") // Empty password
                .build();

        // When/Then
        mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("POST /refresh should return 200 with new access token")
    void refreshShouldReturnSuccess() throws Exception {
        // Given
        RefreshRequest request = RefreshRequest.builder()
                .refreshToken("refresh-token-123")
                .build();

        RefreshResponse response = RefreshResponse.builder()
                .accessToken("new-access-token-456")
                .expiresIn(900)
                .build();

        when(authService.refresh(any(RefreshRequest.class))).thenReturn(response);

        // When/Then
        mockMvc.perform(post("/api/v1/auth/refresh")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accessToken").value("new-access-token-456"))
                .andExpect(jsonPath("$.expiresIn").value(900));
    }

    @Test
    @DisplayName("POST /refresh should return 400 when validation fails")
    void refreshShouldReturn400WhenValidationFails() throws Exception {
        // Given - Invalid request (missing refresh token)
        RefreshRequest request = RefreshRequest.builder()
                .refreshToken("") // Empty token
                .build();

        // When/Then
        mockMvc.perform(post("/api/v1/auth/refresh")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("POST /logout should return 200 with success message")
    void logoutShouldReturnSuccess() throws Exception {
        // Given
        LogoutRequest request = LogoutRequest.builder()
                .refreshToken("refresh-token-123")
                .build();

        LogoutResponse response = LogoutResponse.builder()
                .message("User logged out successfully")
                .build();

        when(authService.logout(any(LogoutRequest.class), any())).thenReturn(response);

        // When/Then
        mockMvc.perform(post("/api/v1/auth/logout")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("User logged out successfully"));
    }

    @Test
    @DisplayName("POST /logout should return 400 when validation fails")
    void logoutShouldReturn400WhenValidationFails() throws Exception {
        // Given - Invalid request (missing refresh token)
        LogoutRequest request = LogoutRequest.builder()
                .refreshToken("") // Empty token
                .build();

        // When/Then
        mockMvc.perform(post("/api/v1/auth/logout")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest());
    }
}

