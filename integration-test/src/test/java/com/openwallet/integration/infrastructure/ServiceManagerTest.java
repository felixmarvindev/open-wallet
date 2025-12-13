package com.openwallet.integration.infrastructure;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class ServiceManagerTest {

    private final InfrastructureInfo infra = new StubInfra();
    private final ServiceManager serviceManager = new ServiceManager(infra);

    @Test
    @DisplayName("Common env should include DB, Kafka, Keycloak, and issuer")
    void commonEnvContainsExpectedValues() {
        Map<String, String> env = serviceManager.commonEnv();

        assertThat(env.get("SPRING_DATASOURCE_URL")).isEqualTo("jdbc:postgresql://localhost:5432/testdb");
        assertThat(env.get("SPRING_KAFKA_BOOTSTRAP-SERVERS")).isEqualTo("kafka:29092");
        assertThat(env.get("KEYCLOAK_SERVER-URL")).isEqualTo("http://kc:8080");
        assertThat(env.get("KEYCLOAK_REALM")).isEqualTo("openwallet");
        assertThat(env.get("SPRING_SECURITY_OAUTH2_RESOURCESERVER_JWT_ISSUER-URI"))
                .isEqualTo("http://kc:8080/realms/openwallet");

        // Ensure Flyway is disabled and JPA set to update
        assertThat(env.get("SPRING_FLYWAY_ENABLED")).isEqualTo("false");
        assertThat(env.get("SPRING_JPA_HIBERNATE_DDL-AUTO")).isEqualTo("update");
    }

    @Test
    @DisplayName("Service-specific env adds application name")
    void serviceSpecificEnvAddsName() {
        assertThat(serviceManager.authEnv().get("SPRING_APPLICATION_NAME")).isEqualTo("auth-service");
        assertThat(serviceManager.customerEnv().get("SPRING_APPLICATION_NAME")).isEqualTo("customer-service");
        assertThat(serviceManager.walletEnv().get("SPRING_APPLICATION_NAME")).isEqualTo("wallet-service");
        assertThat(serviceManager.ledgerEnv().get("SPRING_APPLICATION_NAME")).isEqualTo("ledger-service");
        assertThat(serviceManager.notificationEnv().get("SPRING_APPLICATION_NAME")).isEqualTo("notification-service");
    }

    private static class StubInfra implements InfrastructureInfo {
        @Override public String getPostgresJdbcUrl() { return "jdbc:postgresql://localhost:5432/testdb"; }
        @Override public String getPostgresUsername() { return "user"; }
        @Override public String getPostgresPassword() { return "pass"; }
        @Override public String getKafkaBootstrapServers() { return "kafka:29092"; }
        @Override public String getKeycloakBaseUrl() { return "http://kc:8080"; }
        @Override public String getKeycloakRealm() { return "openwallet"; }
    }
}


