package com.openwallet.integration.infrastructure;

import lombok.Getter;
import lombok.extern.slf4j.Slf4j;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

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
    private LedgerServiceContainer ledgerService;
    
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
        ledgerService = new LedgerServiceContainer(infrastructure);
        
        containers.add(authService);
        containers.add(customerService);
        containers.add(walletService);
        containers.add(ledgerService);
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
     * Start services based on ServiceRequirement types.
     * Only starts the services specified in the requirement.
     * 
     * @param requirements Service types to start
     */
    public void startRequired(ServiceRequirement.ServiceType... requirements) {
        if (requirements == null || requirements.length == 0) {
            log.warn("No service requirements specified, starting all services");
            startAll();
            return;
        }
        
        Set<ServiceRequirement.ServiceType> requiredSet = new HashSet<>(Arrays.asList(requirements));
        long startTime = System.currentTimeMillis();
        
        log.info("========================================");
        log.info("Starting required services: {}", Arrays.toString(requirements));
        log.info("========================================");
        
        List<ServiceContainer> started = new ArrayList<>();
        
        if (requiredSet.contains(ServiceRequirement.ServiceType.AUTH)) {
            authService.start();
            started.add(authService);
        }
        
        if (requiredSet.contains(ServiceRequirement.ServiceType.CUSTOMER)) {
            customerService.start();
            started.add(customerService);
        }

        if (requiredSet.contains(ServiceRequirement.ServiceType.LEDGER)) {
            ledgerService.start();
            started.add(ledgerService);
        }

        if (requiredSet.contains(ServiceRequirement.ServiceType.WALLET)) {
            walletService.start();
            started.add(walletService);
        }

        long duration = (System.currentTimeMillis() - startTime) / 1000;
        log.info("========================================");
        log.info("✓ {} required service(s) started in {} seconds!", started.size(), duration);
        log.info("========================================");
    }
    
    /**
     * Stop only the services that were started via startRequired().
     * 
     * @param requirements Service types to stop
     */
    public void stopRequired(ServiceRequirement.ServiceType... requirements) {
        if (requirements == null || requirements.length == 0) {
            return;
        }
        
        Set<ServiceRequirement.ServiceType> requiredSet = new HashSet<>(Arrays.asList(requirements));
        log.info("Stopping {} required service(s)...", requiredSet.size());
        
        // Stop in reverse order
        if (requiredSet.contains(ServiceRequirement.ServiceType.LEDGER) && ledgerService.isRunning()) {
            ledgerService.stop();
        }
        if (requiredSet.contains(ServiceRequirement.ServiceType.WALLET) && walletService.isRunning()) {
            walletService.stop();
        }
        if (requiredSet.contains(ServiceRequirement.ServiceType.CUSTOMER) && customerService.isRunning()) {
            customerService.stop();
        }
        if (requiredSet.contains(ServiceRequirement.ServiceType.AUTH) && authService.isRunning()) {
            authService.stop();
        }
        
        log.info("✓ Required services stopped");
    }
    
    /**
     * Get service container by type.
     * 
     * @param type Service type
     * @return Service container, or null if not found
     */
    public ServiceContainer getService(ServiceRequirement.ServiceType type) {
        return switch (type) {
            case AUTH -> authService;
            case CUSTOMER -> customerService;
            case WALLET -> walletService;
            case LEDGER -> ledgerService;
        };
    }
    
    /**
     * Check if a specific service is running.
     * 
     * @param type Service type
     * @return true if service is running
     */
    public boolean isServiceRunning(ServiceRequirement.ServiceType type) {
        ServiceContainer service = getService(type);
        return service != null && service.isRunning();
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
