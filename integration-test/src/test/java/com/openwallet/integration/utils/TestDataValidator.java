package com.openwallet.integration.utils;

import lombok.extern.slf4j.Slf4j;
import org.assertj.core.api.Assertions;

import java.util.function.Supplier;

/**
 * Utility for fast-fail validation of test preconditions.
 * Validates test data immediately and fails fast with clear error messages,
 * rather than failing later in the test flow.
 * 
 * Usage:
 * <pre>
 * {@code
 * TestDataValidator.requireServiceRunning(serviceManager, ServiceRequirement.ServiceType.AUTH);
 * TestDataValidator.requireUserExists(userManager, "testuser");
 * TestDataValidator.requireNotNull(token, "Access token");
 * }
 * </pre>
 */
@Slf4j
public class TestDataValidator {
    
    /**
     * Validates that a service is running.
     * Fails immediately if service is not running.
     * 
     * @param serviceManager Service container manager
     * @param serviceType Service type to check
     * @throws AssertionError if service is not running
     */
    public static void requireServiceRunning(
            com.openwallet.integration.infrastructure.ServiceContainerManager serviceManager,
            com.openwallet.integration.infrastructure.ServiceRequirement.ServiceType serviceType) {
        
        if (serviceManager == null) {
            throw new AssertionError("ServiceContainerManager is null");
        }
        
        if (!serviceManager.isServiceRunning(serviceType)) {
            throw new AssertionError(
                String.format("Required service %s is not running. Ensure it is started before running tests.", 
                    serviceType));
        }
        
        log.debug("✓ Service {} is running", serviceType);
    }
    
    /**
     * Validates that a test user exists.
     * Fails immediately if user does not exist.
     * 
     * @param userManager Test user manager
     * @param username Username to check
     * @throws AssertionError if user does not exist
     */
    public static void requireUserExists(TestUserManager userManager, String username) {
        if (userManager == null) {
            throw new AssertionError("TestUserManager is null");
        }
        
        if (!userManager.hasUser(username)) {
            throw new AssertionError(
                String.format("Required test user '%s' does not exist. Create user before running tests.", username));
        }
        
        log.debug("✓ Test user {} exists", username);
    }
    
    /**
     * Validates that a value is not null.
     * Fails immediately if value is null.
     * 
     * @param value Value to check
     * @param name Name of the value (for error message)
     * @throws AssertionError if value is null
     */
    public static void requireNotNull(Object value, String name) {
        if (value == null) {
            throw new AssertionError(String.format("Required value '%s' is null", name));
        }
        log.debug("✓ {} is not null", name);
    }
    
    /**
     * Validates that a value is not null, with custom error message supplier.
     * 
     * @param value Value to check
     * @param errorMessageSupplier Supplier for error message
     * @throws AssertionError if value is null
     */
    public static void requireNotNull(Object value, Supplier<String> errorMessageSupplier) {
        if (value == null) {
            throw new AssertionError(errorMessageSupplier.get());
        }
    }
    
    /**
     * Validates that a string is not blank.
     * Fails immediately if string is null or blank.
     * 
     * @param value String to check
     * @param name Name of the value (for error message)
     * @throws AssertionError if string is blank
     */
    public static void requireNotBlank(String value, String name) {
        if (value == null || value.isBlank()) {
            throw new AssertionError(String.format("Required value '%s' is null or blank", name));
        }
        log.debug("✓ {} is not blank", name);
    }
    
    /**
     * Validates that an HTTP response has a successful status code.
     * Fails immediately if status code is not in 200-299 range.
     * 
     * @param response HTTP response
     * @param operation Description of the operation (for error message)
     * @throws AssertionError if status code is not successful
     */
    public static void requireSuccess(TestHttpClient.HttpResponse response, String operation) {
        if (response == null) {
            throw new AssertionError(String.format("HTTP response is null for operation: %s", operation));
        }
        
        int statusCode = response.getStatusCode();
        if (statusCode < 200 || statusCode >= 300) {
            throw new AssertionError(
                String.format("Operation '%s' failed with status %d: %s", 
                    operation, statusCode, response.getBody()));
        }
        
        log.debug("✓ Operation {} succeeded with status {}", operation, statusCode);
    }
    
    /**
     * Validates multiple services are running.
     * 
     * @param serviceManager Service container manager
     * @param serviceTypes Service types to check
     * @throws AssertionError if any service is not running
     */
    public static void requireServicesRunning(
            com.openwallet.integration.infrastructure.ServiceContainerManager serviceManager,
            com.openwallet.integration.infrastructure.ServiceRequirement.ServiceType... serviceTypes) {
        
        for (com.openwallet.integration.infrastructure.ServiceRequirement.ServiceType type : serviceTypes) {
            requireServiceRunning(serviceManager, type);
        }
    }
}

