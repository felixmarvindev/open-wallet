package com.openwallet.integration.infrastructure;

import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.flyway.FlywayAutoConfiguration;
import org.springframework.context.ConfigurableApplicationContext;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.HashMap;
import java.util.Map;

/**
 * Runs a microservice as an embedded Spring Boot application (not in Docker).
 * This is MUCH faster than building Docker images and starting containers.
 * 
 * Benefits:
 * - Services start in 5-10 seconds (vs 2-5 minutes for Docker)
 * - Easier to debug (direct access to logs)
 * - Industry standard approach for integration testing
 * - Tests real HTTP communication (microservices as black boxes)
 */
@Slf4j
@Getter
public class EmbeddedServiceRunner {
    
    private final String serviceName;
    private final Class<?> mainClass;
    private final int port;
    private final InfrastructureInfo infrastructure;
    private ConfigurableApplicationContext context;
    
    public EmbeddedServiceRunner(
            String serviceName,
            Class<?> mainClass,
            int port,
            InfrastructureInfo infrastructure) {
        this.serviceName = serviceName;
        this.mainClass = mainClass;
        this.port = port;
        this.infrastructure = infrastructure;
    }
    
    /**
     * Start the service as a Spring Boot application.
     * Services run in the same JVM but on different ports.
     */
    public void start() {
        log.info("========================================");
        log.info("Starting {} on port {}...", serviceName, port);
        log.info("========================================");
        log.info("Infrastructure Configuration:");
        log.info("  - Database: {}", infrastructure.getPostgresJdbcUrl());
        log.info("  - Kafka: {}", infrastructure.getKafkaBootstrapServers());
        log.info("  - Keycloak: {}", infrastructure.getKeycloakBaseUrl());
        log.info("  - Keycloak Realm: {}", infrastructure.getKeycloakRealm());
        log.info("========================================");
        
        SpringApplication app = new SpringApplication(mainClass);
        app.setAdditionalProfiles("test");
        
        Map<String, Object> props = buildProperties();
        app.setDefaultProperties(props);

        
        // Use command-line args to override properties (higher precedence than profile configs)
        // This ensures we override the hardcoded localhost:5433 from application-local.yml
        String keycloakIssuerUri = infrastructure.getKeycloakBaseUrl() + "/realms/" + infrastructure.getKeycloakRealm();
        
        String[] args = new String[] {
            // Database configuration (TestContainers PostgreSQL)
            "--spring.datasource.url=" + infrastructure.getPostgresJdbcUrl(),
            "--spring.datasource.username=" + infrastructure.getPostgresUsername(),
            "--spring.datasource.password=" + infrastructure.getPostgresPassword(),
            "--spring.flyway.enabled=false",
            
            // Server configuration
            "--server.port=" + port,
            
            // JPA: auto-create tables instead of validating (overrides application-local.yml)
            "--spring.jpa.hibernate.ddl-auto=update",
            
            // Keycloak configuration (TestContainers Keycloak)
            "--keycloak.server-url=" + infrastructure.getKeycloakBaseUrl(),
            "--keycloak.realm=" + infrastructure.getKeycloakRealm(),
            "--spring.security.oauth2.resourceserver.jwt.issuer-uri=" + keycloakIssuerUri,
            
            // Kafka configuration (TestContainers Kafka)
            "--spring.kafka.bootstrap-servers=" + infrastructure.getKafkaBootstrapServers(),
            "--kafka.bootstrap-servers=" + infrastructure.getKafkaBootstrapServers(),
            
            // Disable health checks for services not available in test environment
            "--management.health.redis.enabled=false",
            // Override readiness group to exclude redis (since it's disabled)
            "--management.endpoint.health.group.readiness.include=db,diskSpace,ping",
            "--spring.data.redis.host=localhost",
            "--spring.data.redis.port=6379"
        };
        
        try {
            context = app.run(args);
            log.info("✓ {} started successfully on port {}", serviceName, port);
            
            // Wait for health endpoint to be ready
            waitForHealth();
        } catch (Exception e) {
            log.error("Failed to start {}: {}", serviceName, e.getMessage(), e);
            throw new IllegalStateException("Failed to start " + serviceName, e);
        }
    }
    
    /**
     * Build Spring Boot properties for the service.
     */
    private Map<String, Object> buildProperties() {
        Map<String, Object> props = new HashMap<>();
        
        // Profile - use ONLY test profile (not local)
        props.put("spring.profiles.active", "test");
        
        // Server config
        props.put("server.port", port);
        props.put("spring.application.name", serviceName);
        
        // Database config - point to TestContainers PostgreSQL
        props.put("spring.datasource.url", infrastructure.getPostgresJdbcUrl());
        props.put("spring.datasource.username", infrastructure.getPostgresUsername());
        props.put("spring.datasource.password", infrastructure.getPostgresPassword());
        props.put("spring.datasource.driver-class-name", "org.postgresql.Driver");
        props.put("spring.jpa.hibernate.ddl-auto", "update");
        props.put("spring.flyway.enabled", "false");
        
        // Kafka config - point to TestContainers Kafka
        props.put("spring.kafka.bootstrap-servers", infrastructure.getKafkaBootstrapServers());
        props.put("kafka.bootstrap-servers", infrastructure.getKafkaBootstrapServers());
        
        // Keycloak config - point to TestContainers Keycloak
        String issuerUri = infrastructure.getKeycloakBaseUrl() + 
                          "/realms/" + infrastructure.getKeycloakRealm();
        props.put("spring.security.oauth2.resourceserver.jwt.issuer-uri", issuerUri);
        props.put("keycloak.server-url", infrastructure.getKeycloakBaseUrl());
        props.put("keycloak.realm", infrastructure.getKeycloakRealm());
        
        // Disable Redis health check if not needed
        props.put("management.health.redis.enabled", "false");
        
        // Enable actuator endpoints for health checks
        props.put("management.endpoints.web.exposure.include", "health,info");
        props.put("management.endpoint.health.show-details", "always");
        
        // Logging
        props.put("logging.level.root", "INFO");
        props.put("logging.level.com.openwallet", "DEBUG");
        
        return props;
    }
    
    /**
     * Stop the service.
     */
    public void stop() {
        if (context != null && context.isRunning()) {
            log.info("Stopping {}...", serviceName);
            context.close();
        }
    }
    
    /**
     * Check if the service is running.
     */
    public boolean isRunning() {
        return context != null && context.isRunning();
    }
    
    /**
     * Wait for service to be healthy.
     * Polls the /actuator/health endpoint until it returns 200.
     */
    private void waitForHealth() throws InterruptedException {
        String healthUrl = "http://localhost:" + port + "/actuator/health";
        int maxAttempts = 60; // 60 seconds timeout
        
        HttpClient client = HttpClient.newBuilder()
                .version(HttpClient.Version.HTTP_1_1)
                .build();
        
        for (int i = 0; i < maxAttempts; i++) {
            try {
                HttpRequest request = HttpRequest.newBuilder()
                        .uri(URI.create(healthUrl))
                        .GET()
                        .timeout(java.time.Duration.ofSeconds(5))
                        .build();
                
                HttpResponse<String> response = 
                        client.send(request, HttpResponse.BodyHandlers.ofString());
                
                if (response.statusCode() == 200) {
                    log.info("✓ {} health check passed", serviceName);
                    return;
                }
                
                // Log response body on first few attempts to help debug
                if (i < 5 || i % 10 == 0) {
                    log.warn("{} health check returned {} - Response: {}", 
                            serviceName, response.statusCode(), response.body());
                } else {
                    log.debug("{} health check returned {}, waiting...", serviceName, response.statusCode());
                }
            } catch (Exception e) {
                // Service not ready yet
                log.debug("{} not ready yet: {}", serviceName, e.getMessage());
            }
            
            Thread.sleep(1000);
        }
        
        throw new IllegalStateException(
                serviceName + " did not become healthy within " + maxAttempts + " seconds. " +
                "Check the service logs for errors.");
    }
    
    /**
     * Get the base URL for this service.
     */
    public String getBaseUrl() {
        return "http://localhost:" + port;
    }
}

