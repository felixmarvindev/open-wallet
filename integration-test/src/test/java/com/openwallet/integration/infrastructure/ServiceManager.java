package com.openwallet.integration.infrastructure;

import lombok.RequiredArgsConstructor;

import java.util.HashMap;
import java.util.Map;

/**
 * Provides environment/properties maps for starting microservices against the
 * TestContainers infrastructure. This class does NOT start services; it only
 * prepares the configuration so callers can launch services (via Spring Boot
 * apps or processes) and then wait for health using {@link HealthWaiter}.
 */
@RequiredArgsConstructor
public class ServiceManager {

    private final InfrastructureInfo infra;

    /**
     * Returns a map of common Spring Boot properties pointing services to the
     * running TestContainers infrastructure.
     */
    public Map<String, String> commonEnv() {
        Map<String, String> env = new HashMap<>();

        // Database
        env.put("SPRING_DATASOURCE_URL", infra.getPostgresJdbcUrl());
        env.put("SPRING_DATASOURCE_USERNAME", infra.getPostgresUsername());
        env.put("SPRING_DATASOURCE_PASSWORD", infra.getPostgresPassword());
        env.put("SPRING_DATASOURCE_DRIVER-CLASS-NAME", "org.postgresql.Driver");

        // Kafka
        env.put("SPRING_KAFKA_BOOTSTRAP-SERVERS", infra.getKafkaBootstrapServers());
        env.put("KAFKA_BOOTSTRAP-SERVERS", infra.getKafkaBootstrapServers());

        // Keycloak / JWT issuer
        String issuer = infra.getKeycloakBaseUrl() + "/realms/" + infra.getKeycloakRealm();
        env.put("KEYCLOAK_SERVER-URL", infra.getKeycloakBaseUrl());
        env.put("KEYCLOAK_REALM", infra.getKeycloakRealm());
        env.put("SPRING_SECURITY_OAUTH2_RESOURCESERVER_JWT_ISSUER-URI", issuer);

        // Migrations and JPA defaults for tests
        env.put("SPRING_FLYWAY_ENABLED", "false");
        env.put("SPRING_JPA_HIBERNATE_DDL-AUTO", "update");
        env.put("SPRING_JPA_SHOW-SQL", "false");

        return env;
    }

    /**
     * Returns env/properties map tailored for the Auth service.
     */
    public Map<String, String> authEnv() {
        Map<String, String> env = commonEnv();
        env.put("SPRING_APPLICATION_NAME", "auth-service");
        return env;
    }

    /**
     * Returns env/properties map tailored for the Customer service.
     */
    public Map<String, String> customerEnv() {
        Map<String, String> env = commonEnv();
        env.put("SPRING_APPLICATION_NAME", "customer-service");
        return env;
    }

    /**
     * Returns env/properties map tailored for the Wallet service.
     */
    public Map<String, String> walletEnv() {
        Map<String, String> env = commonEnv();
        env.put("SPRING_APPLICATION_NAME", "wallet-service");
        return env;
    }

    /**
     * Returns env/properties map tailored for the Ledger service.
     */
    public Map<String, String> ledgerEnv() {
        Map<String, String> env = commonEnv();
        env.put("SPRING_APPLICATION_NAME", "ledger-service");
        return env;
    }

    /**
     * Returns env/properties map tailored for the Notification service.
     */
    public Map<String, String> notificationEnv() {
        Map<String, String> env = commonEnv();
        env.put("SPRING_APPLICATION_NAME", "notification-service");
        return env;
    }
}


