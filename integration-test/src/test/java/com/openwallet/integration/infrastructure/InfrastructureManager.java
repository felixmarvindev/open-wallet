package com.openwallet.integration.infrastructure;


import dasniko.testcontainers.keycloak.KeycloakContainer;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.testcontainers.containers.KafkaContainer;
import org.testcontainers.containers.Network;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;

import java.time.Duration;

/**
 * Manages TestContainers infrastructure for integration tests.
 * Provides PostgreSQL, Kafka, and Keycloak containers with proper configuration.
 */
@Slf4j
@Getter
public class InfrastructureManager implements InfrastructureInfo {

    private Network network;
    private PostgreSQLContainer<?> postgresContainer;
    private KafkaContainer kafkaContainer;
    private KeycloakContainer keycloakContainer;

    private String postgresJdbcUrl;
    private String postgresUsername;
    private String postgresPassword;
    private String kafkaBootstrapServers;
    private String keycloakBaseUrl;
    private String keycloakRealm;

    /**
     * Starts all infrastructure containers.
     */
    public void start() {
        log.info("Starting infrastructure containers...");

        // Create shared network for containers to communicate
        network = Network.newNetwork();
        log.info("Created Docker network: {}", network.getId());

        // Start PostgreSQL
        startPostgreSQL();

        // Start Kafka
        startKafka();

        // Start Keycloak (depends on PostgreSQL)
        startKeycloak();

        log.info("All infrastructure containers started successfully");
        log.info("PostgreSQL: {}", postgresJdbcUrl);
        log.info("Kafka: {}", kafkaBootstrapServers);
        log.info("Keycloak: {}", keycloakBaseUrl);
    }

    /**
     * Stops all infrastructure containers.
     */
    public void stop() {
        log.info("Stopping infrastructure containers...");

        if (keycloakContainer != null && keycloakContainer.isRunning()) {
            keycloakContainer.stop();
        }

        if (kafkaContainer != null && kafkaContainer.isRunning()) {
            kafkaContainer.stop();
        }

        if (postgresContainer != null && postgresContainer.isRunning()) {
            postgresContainer.stop();
        }

        log.info("All infrastructure containers stopped");
    }

    private void startPostgreSQL() {
        log.info("Starting PostgreSQL container...");
        postgresContainer = new PostgreSQLContainer<>(DockerImageName.parse("postgres:15-alpine"))
                .withDatabaseName("openwallet_test")
                .withUsername("openwallet")
                .withPassword("openwallet")
                .withNetwork(network)
                .withNetworkAliases("postgres")
                .withReuse(true); // Reuse container across test runs if possible

        postgresContainer.start();

        postgresJdbcUrl = postgresContainer.getJdbcUrl();
        postgresUsername = postgresContainer.getUsername();
        postgresPassword = postgresContainer.getPassword();

        log.info("PostgreSQL started at: {}", postgresJdbcUrl);
    }

    private void startKafka() {
        log.info("Starting Kafka container...");
        kafkaContainer = new KafkaContainer(DockerImageName.parse("confluentinc/cp-kafka:7.5.0"))
                .withNetwork(network)
                .withNetworkAliases("kafka")
                .withReuse(true); // Reuse container across test runs if possible

        kafkaContainer.start();

        kafkaBootstrapServers = kafkaContainer.getBootstrapServers();

        log.info("Kafka started at: {}", kafkaBootstrapServers);
    }

    private void startKeycloak() {
        log.info("Starting Keycloak container...");
        
        // Use dasniko testcontainers-keycloak library
        // Configure Keycloak to use PostgreSQL database
        // Build the database URL using network alias
        String dbUrl = String.format("jdbc:postgresql://%s:%d/%s",
                "postgres", // Use network alias
                5432, // Internal port
                postgresContainer.getDatabaseName());
        
        log.info("Keycloak database URL: {}", dbUrl);
        log.info("Keycloak database user: {}", postgresContainer.getUsername());
        
        try {
            keycloakContainer = new KeycloakContainer("quay.io/keycloak/keycloak:26.0.7")
                    .withNetwork(network)
                    .withNetworkAliases("keycloak")
                    .withAdminUsername("admin")
                    .withAdminPassword("admin")
                    .withReuse(true) // Reuse container across test runs if possible
                    .withStartupTimeout(Duration.ofMinutes(3))
                    // Import realm configuration with clients and users
                    .withRealmImportFile("openwallet-realm.json")
                    // Configure database connection to use PostgreSQL container
                    .withEnv("KC_DB", "postgres")
                    .withEnv("KC_DB_URL", dbUrl)
                    .withEnv("KC_DB_USERNAME", postgresContainer.getUsername())
                    .withEnv("KC_DB_PASSWORD", postgresContainer.getPassword())
                    .withEnv("KC_HTTP_ENABLED", "true")
                    .withEnv("KC_HEALTH_ENABLED", "true")
                    .withEnv("KC_METRICS_ENABLED", "true")
                    .withEnv("KC_HOSTNAME_STRICT", "false")
                    .withEnv("KC_HOSTNAME_STRICT_HTTPS", "false");

            keycloakContainer.start();

            keycloakBaseUrl = keycloakContainer.getAuthServerUrl();
            keycloakRealm = "openwallet";

            log.info("Keycloak started successfully at: {}", keycloakBaseUrl);
            log.info("Keycloak realm '{}' imported with clients and users", keycloakRealm);
            log.info("  - Test user: admin/admin (ADMIN role)");
            log.info("  - Test user: testuser/testpass (USER, CUSTOMER roles)");
            log.info("  - Clients: auth-service, customer-service, wallet-service, frontend-app");
        } catch (Exception e) {
            log.error("Failed to start Keycloak container", e);
            if (keycloakContainer != null && keycloakContainer.getContainerId() != null) {
                log.error("Keycloak container logs:\n{}", keycloakContainer.getLogs());
            }
            throw new RuntimeException("Failed to start Keycloak container: " + e.getMessage(), e);
        }
    }
}

