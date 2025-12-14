package com.openwallet.integration.infrastructure;

import com.openwallet.auth.AuthServiceApplication;

/**
 * Container for the Auth Service.
 * Manages the lifecycle of the auth-service microservice.
 */
public class AuthServiceContainer extends ServiceContainer {
    
    public static final int DEFAULT_PORT = 9001;
    
    public AuthServiceContainer(InfrastructureInfo infrastructure) {
        super("auth-service", DEFAULT_PORT, infrastructure);
    }
    
    public AuthServiceContainer(int port, InfrastructureInfo infrastructure) {
        super("auth-service", port, infrastructure);
    }
    
    @Override
    protected Class<?> getMainClass() {
        return AuthServiceApplication.class;
    }
}

