package com.openwallet.integration.infrastructure;

import com.openwallet.customer.CustomerServiceApplication;

/**
 * Container for the Customer Service.
 * Manages the lifecycle of the customer-service microservice.
 */
public class CustomerServiceContainer extends ServiceContainer {
    
    public static final int DEFAULT_PORT = 9002;
    
    public CustomerServiceContainer(InfrastructureInfo infrastructure) {
        super("customer-service", DEFAULT_PORT, infrastructure);
    }
    
    public CustomerServiceContainer(int port, InfrastructureInfo infrastructure) {
        super("customer-service", port, infrastructure);
    }
    
    @Override
    protected Class<?> getMainClass() {
        return CustomerServiceApplication.class;
    }
}

