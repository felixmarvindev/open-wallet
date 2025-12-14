package com.openwallet.integration.infrastructure;

import lombok.Getter;
import lombok.extern.slf4j.Slf4j;

import java.util.ArrayList;
import java.util.List;

/**
 * Manages all service containers for integration tests.
 * Provides a centralized way to start, stop, and manage microservices.
 * 
 * Usage:
 * <pre>
 * {@code
 * ServiceContainerManager manager = new ServiceContainerManager(infrastructure);
 * manager.startAll();
 * 
 * // Run tests...
 * 
 * manager.stopAll();
 * }
 * </pre>
 */
@Slf4j
@Getter
public class ServiceContainerManager {
    
    private final InfrastructureInfo infrastructure;
    private final List<ServiceContainer> containers = new ArrayList<>();
    
    private AuthServiceContainer authService;
    private CustomerServiceContainer customerService;
    private WalletServiceContainer walletService;
    
    public ServiceContainerManager(InfrastructureInfo infrastructure) {
        this.infrastructure = infrastructure;
        initializeContainers();
    }
    
    /**
     * Initialize all service containers.
     */
    private void initializeContainers() {
        authService = new AuthServiceContainer(infrastructure);
        customerService = new CustomerServiceContainer(infrastructure);
        walletService = new WalletServiceContainer(infrastructure);
        
        containers.add(authService);
        containers.add(customerService);
        containers.add(walletService);
    }
    
    /**
     * Start all services.
     */
    public void startAll() {
        long startTime = System.currentTimeMillis();
        log.info("========================================");
        log.info("Starting all services...");
        log.info("========================================");
        
        for (ServiceContainer container : containers) {
            container.start();
        }
        
        long duration = (System.currentTimeMillis() - startTime) / 1000;
        log.info("========================================");
        log.info("✓ All {} services started in {} seconds!", containers.size(), duration);
        log.info("========================================");
    }
    
    /**
     * Start specific services.
     */
    public void start(ServiceContainer... services) {
        log.info("Starting {} service(s)...", services.length);
        for (ServiceContainer service : services) {
            service.start();
        }
    }
    
    /**
     * Stop all services.
     */
    public void stopAll() {
        log.info("Stopping all services...");
        
        // Stop in reverse order
        for (int i = containers.size() - 1; i >= 0; i--) {
            containers.get(i).stop();
        }
        
        log.info("✓ All services stopped");
    }
    
    /**
     * Stop specific services.
     */
    public void stop(ServiceContainer... services) {
        log.info("Stopping {} service(s)...", services.length);
        for (ServiceContainer service : services) {
            service.stop();
        }
    }
    
    /**
     * Check if all services are running.
     */
    public boolean allRunning() {
        return containers.stream().allMatch(ServiceContainer::isRunning);
    }
    
    /**
     * Get a summary of running services.
     */
    public String getStatus() {
        StringBuilder status = new StringBuilder();
        status.append("Service Status:\n");
        for (ServiceContainer container : containers) {
            status.append(String.format("  - %s: %s (port %d)%n", 
                    container.getServiceName(),
                    container.isRunning() ? "RUNNING" : "STOPPED",
                    container.getPort()));
        }
        return status.toString();
    }
}
