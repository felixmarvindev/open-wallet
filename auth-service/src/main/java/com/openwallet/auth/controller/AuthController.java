package com.openwallet.auth.controller;

import com.openwallet.auth.dto.*;
import com.openwallet.auth.service.AuthService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * REST controller for authentication and user management endpoints.
 */
@RestController
@RequestMapping("/api/v1/auth")
@RequiredArgsConstructor
@Slf4j
@SuppressWarnings("null")
public class AuthController {

    private final AuthService authService;

    /**
     * Register a new user.
     *
     * @param request Registration request
     * @return Registration response with userId
     */
    @PostMapping("/register")
    public ResponseEntity<RegisterResponse> register(@Valid @RequestBody RegisterRequest request) {
        log.info("Received registration request: username={}", request.getUsername());
        RegisterResponse response = authService.register(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    /**
     * Authenticate a user and obtain JWT tokens.
     *
     * @param request Login request
     * @return Login response with access token and refresh token
     */
    @PostMapping("/login")
    public ResponseEntity<LoginResponse> login(@Valid @RequestBody LoginRequest request) {
        log.info("Received login request: username={}", request.getUsername());
        LoginResponse response = authService.login(request);
        return ResponseEntity.ok(response);
    }

    /**
     * Refresh an access token using a refresh token.
     *
     * @param request Refresh request with refresh token
     * @return Refresh response with a new access token
     */
    @PostMapping("/refresh")
    public ResponseEntity<RefreshResponse> refresh(@Valid @RequestBody RefreshRequest request) {
        log.info("Received token refresh request");
        RefreshResponse response = authService.refresh(request);
        return ResponseEntity.ok(response);
    }

    /**
     * Log out a user.
     * Note: In production, userId should be extracted from a JWT token.
     * For now, this endpoint accepts a logout request with a refresh token.
     *
     * @param request Logout request
     * @return Logout response
     */
    @PostMapping("/logout")
    public ResponseEntity<LogoutResponse> logout(@Valid @RequestBody LogoutRequest request) {
        log.info("Received logout request");
        // For now, logout without userId - in production, extract from JWT
        // This is a simplified implementation
        LogoutResponse response = authService.logout(request, null);
        return ResponseEntity.ok(response);
    }
}

