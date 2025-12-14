package com.openwallet.integration.infrastructure;

import lombok.Getter;
import lombok.extern.slf4j.Slf4j;

/**
 * Abstract base class for service containers.
 * Each microservice has its own container that manages its lifecycle.
 */
@Slf4j
@Getter
public abstract class ServiceContainer {

    /**
     * -- GETTER --
     *  Get the service name.
     */
    protected final String serviceName;
    /**
     * -- GETTER --
     *  Get the port.
     */
    protected final int port;
    protected final InfrastructureInfo infrastructure;
    protected EmbeddedServiceRunner runner;
    
    protected ServiceContainer(String serviceName, int port, InfrastructureInfo infrastructure) {
        this.serviceName = serviceName;
        this.port = port;
        this.infrastructure = infrastructure;
    }
    
    /**
     * Get the main application class for this service.
     */
    protected abstract Class<?> getMainClass();
    
    /**
     * Start the service.
     */
    public void start() {
        log.info("Starting {} container...", serviceName);
        runner = new EmbeddedServiceRunner(
                serviceName,
                getMainClass(),
                port,
                infrastructure
        );
        runner.start();
    }
    
    /**
     * Stop the service.
     */
    public void stop() {
        if (runner != null) {
            log.info("Stopping {} container...", serviceName);
            runner.stop();
        }
    }
    
    /**
     * Check if the service is running.
     */
    public boolean isRunning() {
        return runner != null && runner.isRunning();
    }
    
    /**
     * Get the base URL for this service.
     */
    public String getBaseUrl() {
        return runner != null ? runner.getBaseUrl() : null;
    }

}

