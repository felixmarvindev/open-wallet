package com.openwallet.integration;

import org.junit.jupiter.api.Test;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.utility.DockerImageName;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Simple test to verify Testcontainers can connect to Docker
 */
class SimpleDockerTest {

    @Test
    void shouldConnectToDockerAndStartContainer() {
        System.out.println("========================================");
        System.out.println("Starting simple Docker connectivity test");
        System.out.println("DOCKER_HOST env: " + System.getenv("DOCKER_HOST"));
        System.out.println("docker.host property: " + System.getProperty("docker.host"));
        System.out.println("========================================");

        // Try to start the simplest possible container
        try (GenericContainer<?> container = new GenericContainer<>(DockerImageName.parse("alpine:3.19"))
                .withCommand("echo", "Hello from Docker!")) {

            System.out.println("Starting Alpine container...");
            container.start();

            System.out.println("✅ Container started successfully!");
            System.out.println("Container ID: " + container.getContainerId());
            System.out.println("Container is running: " + container.isRunning());

            assertThat(container.isRunning()).isTrue();

            System.out.println("✅ Test PASSED! Docker connection works!");
        } catch (Exception e) {
            System.err.println("❌ Test FAILED!");
            System.err.println("Error: " + e.getMessage());
            e.printStackTrace();
            throw e;
        }
    }
}