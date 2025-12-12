package com.openwallet.auth.config;

import org.keycloak.admin.client.Keycloak;
import org.keycloak.admin.client.KeycloakBuilder;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestTemplate;

/**
 * Configuration for Keycloak Admin Client and HTTP client.
 * Used to interact with Keycloak Admin API for user management operations.
 */
@Configuration
public class KeycloakConfig {

    @Value("${keycloak.server-url}")
    private String serverUrl;

    @Value("${keycloak.realm}")
    private String realm;

    @Value("${keycloak.admin-username:admin}")
    private String adminUsername;

    @Value("${keycloak.admin-password:admin}")
    private String adminPassword;

    /**
     * Creates a Keycloak Admin Client instance using admin credentials.
     * This client is used for administrative operations like creating users,
     * assigning roles, and managing user sessions.
     */
    @Bean
    public Keycloak keycloakAdminClient() {
        return KeycloakBuilder.builder()
                .serverUrl(serverUrl)
                .realm("master") // Admin operations use master realm
                .username(adminUsername)
                .password(adminPassword)
                .clientId("admin-cli") // Default admin CLI client
                .build();
    }

    /**
     * Creates a RestTemplate bean for HTTP requests to Keycloak Token Endpoint.
     */
    @Bean
    public RestTemplate restTemplate() {
        return new RestTemplate();
    }
}

