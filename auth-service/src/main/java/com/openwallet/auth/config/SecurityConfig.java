package com.openwallet.auth.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

/**
 * Security configuration for Auth Service.
 * Auth service issues tokens via Keycloak, so it doesn't validate JWT tokens.
 * Public endpoints (register, login) are permitted, while other endpoints may require authentication.
 */
@Configuration
@EnableWebSecurity
public class SecurityConfig {

    @Bean
    @Order(2)
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
            .csrf(csrf -> csrf.disable())
            .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
            .authorizeHttpRequests(auth -> auth
                // Permit actuator endpoints
                .requestMatchers("/actuator/**").permitAll()
                // Permit public auth endpoints (register, login)
                .requestMatchers("/api/v1/auth/register", "/api/v1/auth/login").permitAll()
                // All other endpoints require authentication (will be configured when controllers are added)
                .anyRequest().authenticated()
            );

        // Ensure CorrelationIdFilter runs before security filter
        http.addFilterBefore(new CorrelationIdFilter(), UsernamePasswordAuthenticationFilter.class);

        return http.build();
    }
}

