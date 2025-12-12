package com.openwallet.integration;

import com.openwallet.integration.infrastructure.InfrastructureManager;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Test to verify that infrastructure containers start correctly.
 * This is a basic smoke test for Phase 1 setup.
 * 
 * Note: This test does not use Spring Boot context as it only tests
 * container startup, not Spring Boot integration.
 */
@DisplayName("Infrastructure Setup Test")
class InfrastructureTest {

    private static InfrastructureManager infrastructureManager;

    @BeforeAll
    static void startInfrastructure() {
        infrastructureManager = new InfrastructureManager();
        infrastructureManager.start();
    }

    @AfterAll
    static void stopInfrastructure() {
        if (infrastructureManager != null) {
            infrastructureManager.stop();
        }
    }

    @Test
    @DisplayName("Should start all infrastructure containers successfully")
    void shouldStartInfrastructureContainers() {
        // Verify infrastructure is available
        assertThat(infrastructureManager).isNotNull();

        // Verify PostgreSQL
        assertThat(infrastructureManager.getPostgresJdbcUrl()).isNotBlank();
        assertThat(infrastructureManager.getPostgresUsername()).isEqualTo("openwallet");
        assertThat(infrastructureManager.getPostgresPassword()).isEqualTo("openwallet");

        // Verify Kafka
        assertThat(infrastructureManager.getKafkaBootstrapServers()).isNotBlank();
        assertThat(infrastructureManager.getKafkaBootstrapServers()).contains(":");

        // Verify Keycloak
        assertThat(infrastructureManager.getKeycloakBaseUrl()).isNotBlank();
        assertThat(infrastructureManager.getKeycloakBaseUrl()).startsWith("http://");
        assertThat(infrastructureManager.getKeycloakRealm()).isEqualTo("openwallet");
    }

    @Test
    @DisplayName("Should provide accessible connection strings")
    void shouldProvideAccessibleConnectionStrings() {
        // Verify connection strings are accessible
        String postgresUrl = infrastructureManager.getPostgresJdbcUrl();
        String kafkaServers = infrastructureManager.getKafkaBootstrapServers();
        String keycloakUrl = infrastructureManager.getKeycloakBaseUrl();

        assertThat(postgresUrl).isNotBlank();
        assertThat(kafkaServers).isNotBlank();
        assertThat(keycloakUrl).isNotBlank();

        // Verify format
        assertThat(postgresUrl).startsWith("jdbc:postgresql://");
        assertThat(keycloakUrl).startsWith("http://");
    }
}

