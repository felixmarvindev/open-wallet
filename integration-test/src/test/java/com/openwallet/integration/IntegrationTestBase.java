package com.openwallet.integration;

import com.openwallet.integration.infrastructure.InfrastructureManager;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

/**
 * Base class for all integration tests.
 * 
 * This class:
 * - Starts TestContainers infrastructure (PostgreSQL, Kafka, Keycloak)
 * - Provides dynamic property configuration for Spring Boot tests
 * - Manages container lifecycle
 * 
 * Usage:
 * <pre>
 * {@code
 * class MyIntegrationTest extends IntegrationTestBase {
 *     @Test
 *     void myTest() {
 *         // Test code here
 *         // Infrastructure is already started
 *     }
 * }
 * }
 * </pre>
 */
@Slf4j
@Getter
public abstract class IntegrationTestBase {

    private static InfrastructureManager infrastructureManager;
    private static boolean infrastructureStarted = false;

    /**
     * Starts infrastructure containers before all tests.
     * Uses singleton pattern to ensure containers are started only once.
     */
    @BeforeAll
    static void startInfrastructure() {
        if (!infrastructureStarted) {
            log.info("Initializing infrastructure for integration tests...");
            infrastructureManager = new InfrastructureManager();
            infrastructureManager.start();
            infrastructureStarted = true;
            log.info("Infrastructure initialization complete");
        }
    }

    /**
     * Stops infrastructure containers after all tests.
     * Note: Containers are reused across test classes for performance.
     */
    @AfterAll
    static void stopInfrastructure() {
        // Note: We don't stop containers here to allow reuse across test classes.
        // Containers will be stopped when JVM exits or explicitly stopped.
        // For explicit cleanup, call infrastructureManager.stop() in a test lifecycle hook if needed.
        log.info("Test class completed. Infrastructure containers remain running for reuse.");
    }

    /**
     * Registers dynamic properties for Spring Boot tests.
     * These properties override application.yml values to point to TestContainers.
     */
    @DynamicPropertySource
    static void configureProperties(DynamicPropertyRegistry registry) {
        if (infrastructureManager == null) {
            throw new IllegalStateException(
                    "InfrastructureManager not initialized. Ensure @BeforeAll startInfrastructure() runs first.");
        }

        // PostgreSQL configuration
        registry.add("spring.datasource.url", infrastructureManager::getPostgresJdbcUrl);
        registry.add("spring.datasource.username", infrastructureManager::getPostgresUsername);
        registry.add("spring.datasource.password", infrastructureManager::getPostgresPassword);
        registry.add("spring.datasource.driver-class-name", () -> "org.postgresql.Driver");

        // Kafka configuration
        registry.add("kafka.bootstrap-servers", infrastructureManager::getKafkaBootstrapServers);
        registry.add("spring.kafka.bootstrap-servers", infrastructureManager::getKafkaBootstrapServers);

        // Keycloak configuration
        registry.add("keycloak.server-url", infrastructureManager::getKeycloakBaseUrl);
        registry.add("keycloak.realm", infrastructureManager::getKeycloakRealm);
        registry.add("spring.security.oauth2.resourceserver.jwt.issuer-uri",
                () -> infrastructureManager.getKeycloakBaseUrl() + "/realms/" + infrastructureManager.getKeycloakRealm());

        // Disable Flyway for integration tests (we'll handle migrations manually if needed)
        registry.add("spring.flyway.enabled", () -> "false");

        // JPA configuration
        registry.add("spring.jpa.hibernate.ddl-auto", () -> "update");
        registry.add("spring.jpa.show-sql", () -> "false");

        log.info("Dynamic properties configured for Spring Boot tests");
    }

    /**
     * Gets the infrastructure manager instance.
     * 
     * @return InfrastructureManager instance
     */
    protected static InfrastructureManager getInfrastructure() {
        if (infrastructureManager == null) {
            throw new IllegalStateException("Infrastructure not initialized. Call startInfrastructure() first.");
        }
        return infrastructureManager;
    }

    /**
     * Gets PostgreSQL JDBC URL.
     * 
     * @return JDBC URL
     */
    protected static String getPostgresUrl() {
        return getInfrastructure().getPostgresJdbcUrl();
    }

    /**
     * Gets Kafka bootstrap servers.
     * 
     * @return Bootstrap servers string
     */
    protected static String getKafkaBootstrapServers() {
        return getInfrastructure().getKafkaBootstrapServers();
    }

    /**
     * Gets Keycloak base URL.
     * 
     * @return Keycloak base URL
     */
    protected static String getKeycloakBaseUrl() {
        return getInfrastructure().getKeycloakBaseUrl();
    }
}


