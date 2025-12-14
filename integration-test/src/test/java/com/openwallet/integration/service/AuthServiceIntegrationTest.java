package com.openwallet.integration.service;

import com.openwallet.integration.IntegrationTestBase;
import com.openwallet.integration.infrastructure.AuthServiceContainer;
import lombok.extern.slf4j.Slf4j;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.IOException;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Integration test that starts auth-service as an embedded Spring Boot application
 * and verifies it can connect to infrastructure and respond to HTTP requests.
 */
@Slf4j
public class AuthServiceIntegrationTest extends IntegrationTestBase {

    private AuthServiceContainer authService;
    private final OkHttpClient httpClient = new OkHttpClient();

    @BeforeEach
    void startAuthService() {
        log.info("Starting auth-service for integration tests...");
        authService = new AuthServiceContainer(getInfrastructure());
        authService.start();
        log.info("Auth service started at: {}", authService.getBaseUrl());
    }

    @AfterEach
    void stopAuthService() {
        if (authService != null) {
            log.info("Stopping auth-service...");
            authService.stop();
        }
    }

    @Test
    @DisplayName("Auth service starts and health endpoint responds")
    void authServiceHealthCheck() throws IOException {
        String healthUrl = authService.getBaseUrl() + "/actuator/health";
        log.info("Testing health endpoint at: {}", healthUrl);

        // Service should already be healthy (EmbeddedServiceRunner waits for health)
        try (Response response = httpClient.newCall(
                new Request.Builder().url(healthUrl).build()).execute()) {
            assertThat(response.code()).isEqualTo(200);
            assertThat(response.body()).isNotNull();
            String body = response.body().string();
            log.info("Health check response: {}", body);
            assertThat(body).contains("UP");
        }
    }

    @Test
    @DisplayName("Auth service info endpoint is accessible")
    void authServiceInfoEndpoint() throws IOException {
        String infoUrl = authService.getBaseUrl() + "/actuator/info";
        log.info("Checking info endpoint at {}", infoUrl);

        try (Response response = httpClient.newCall(
                new Request.Builder().url(infoUrl).build()).execute()) {
            assertThat(response.code()).isEqualTo(200);
        }
    }

    @Test
    @DisplayName("Auth service register endpoint is accessible")
    void authServiceRegisterEndpoint() throws IOException {
        String registerUrl = authService.getBaseUrl() + "/api/v1/auth/register";
        log.info("Checking register endpoint at {}", registerUrl);

        // Just verify the endpoint exists (will return 400 for invalid request, not 404)
        try (Response response = httpClient.newCall(
                new Request.Builder()
                        .url(registerUrl)
                        .post(okhttp3.RequestBody.create("{}", okhttp3.MediaType.get("application/json")))
                        .build()).execute()) {
            // Should return 400 (bad request) for invalid payload, not 404 (not found)
            assertThat(response.code()).isIn(400, 401, 403);
            log.info("Register endpoint responded with status: {}", response.code());
        }
    }
}
