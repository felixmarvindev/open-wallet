package com.openwallet.ledger.config;

import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.core.annotation.Order;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.web.SecurityFilterChain;

import java.time.Instant;
import java.util.Collections;

/**
 * Test security configuration that disables security for testing.
 * This allows tests to run without requiring a real Keycloak instance.
 */
@TestConfiguration
@EnableWebSecurity
public class TestSecurityConfig {

    /**
     * Provides a mock JWT decoder for testing that always returns a valid JWT.
     * This prevents the "No qualifying bean of type JwtDecoder" error.
     */
    @Bean
    @Primary
    public JwtDecoder jwtDecoder() {
        // Return a decoder that always returns a valid (but fake) JWT
        // This is just to satisfy the bean requirement - it won't be used since we permit all requests
        return token -> Jwt.withTokenValue(token)
                .header("alg", "RS256")
                .claim("sub", "test-user")
                .claim("realm_access", Collections.singletonMap("roles", Collections.singletonList("USER")))
                .issuedAt(Instant.now())
                .expiresAt(Instant.now().plusSeconds(3600))
                .issuer("https://test-issuer")
                .build();
    }

    /**
     * Provides a security filter chain that allows all requests for testing.
     * This bypasses authentication in test scenarios.
     */
    @Bean
    @Primary
    @Order(1)
    public SecurityFilterChain testSecurityFilterChain(HttpSecurity http) throws Exception {
        http
            .csrf(csrf -> csrf.disable())
            .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
            .authorizeHttpRequests(auth -> auth
                .anyRequest().permitAll()
            );
        return http.build();
    }
}

