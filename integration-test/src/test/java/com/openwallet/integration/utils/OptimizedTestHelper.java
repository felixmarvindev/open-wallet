package com.openwallet.integration.utils;

import com.openwallet.integration.IntegrationTestBase;
import com.openwallet.integration.infrastructure.InfrastructureManager;
import com.openwallet.integration.infrastructure.ServiceContainerManager;
import com.openwallet.integration.infrastructure.ServiceRequirement;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;

import java.lang.reflect.Method;

/**
 * Helper class for optimized integration tests.
 * Provides utilities for selective service startup, test user management,
 * and fast-fail validation.
 * 
 * Usage:
 * <pre>
 * {@code
 * public class MyTest extends IntegrationTestBase {
 *     private OptimizedTestHelper testHelper;
 *     
 *     @BeforeEach
 *     void setUp() {
 *         testHelper = new OptimizedTestHelper(getInfrastructure());
 *         testHelper.startRequiredServices(this); // Auto-detects @ServiceRequirement
 *         testHelper.createTestUsers(); // Pre-create users
 *     }
 *     
 *     @AfterEach
 *     void tearDown() {
 *         testHelper.cleanup();
 *     }
 * }
 * }
 * </pre>
 */
@Slf4j
@Getter
public class OptimizedTestHelper {
    
    private final ServiceContainerManager serviceManager;
    private final InfrastructureManager infrastructure;
    private TestUserManager userManager;
    private TestHttpClient authClient;
    private boolean servicesStarted = false;
    
    public OptimizedTestHelper(InfrastructureManager infrastructure) {
        this.infrastructure = infrastructure;
        this.serviceManager = new ServiceContainerManager(infrastructure);
    }
    
    /**
     * Initializes clients after services are started.
     * Must be called after startRequiredServices().
     */
    private void initializeClients() {
        if (authClient == null) {
            // Ensure auth service is running before creating client
            if (!serviceManager.isServiceRunning(ServiceRequirement.ServiceType.AUTH)) {
                throw new IllegalStateException(
                    "Auth service is not running. Call startRequiredServices() first.");
            }
            this.authClient = new TestHttpClient(serviceManager.getAuthService().getBaseUrl());
            this.userManager = new TestUserManager(authClient);
        }
    }
    
    /**
     * Starts required services based on @ServiceRequirement annotation on test class.
     * If no annotation is present, starts all services (backward compatibility).
     * Also initializes clients after services are started.
     * 
     * @param testClass Test class instance
     */
    public void startRequiredServices(Object testClass) {
        ServiceRequirement annotation = testClass.getClass().getAnnotation(ServiceRequirement.class);
        
        if (annotation != null && annotation.value().length > 0) {
            log.info("Starting required services based on @ServiceRequirement: {}", 
                java.util.Arrays.toString(annotation.value()));
            serviceManager.startRequired(annotation.value());
        } else {
            log.info("No @ServiceRequirement annotation found, starting all services");
            serviceManager.startAll();
        }
        
        servicesStarted = true;
        // Initialize clients after services are started
        initializeClients();
    }
    
    /**
     * Starts required services for a specific test method.
     * Checks for @ServiceRequirement on method first, then class.
     * 
     * @param testClass Test class instance
     * @param methodName Test method name
     */
    public void startRequiredServices(Object testClass, String methodName) {
        try {
            Method method = testClass.getClass().getMethod(methodName);
            ServiceRequirement methodAnnotation = method.getAnnotation(ServiceRequirement.class);
            
            if (methodAnnotation != null && methodAnnotation.value().length > 0) {
                log.info("Starting required services for method {}: {}", 
                    methodName, java.util.Arrays.toString(methodAnnotation.value()));
                serviceManager.startRequired(methodAnnotation.value());
                return;
            }
        } catch (NoSuchMethodException e) {
            // Method not found, fall through to class-level annotation
        }
        
        // Fall back to class-level annotation or all services
        startRequiredServices(testClass);
    }
    
    /**
     * Creates common test users that can be reused across tests.
     * Override this method in subclasses to create custom users.
     * 
     * Note: This is a no-op by default. Subclasses should override to create users.
     */
    public void createTestUsers() {
        // Default: no users created
        // Subclasses should override this method to create specific test users
        log.debug("No default test users created. Override createTestUsers() to create users.");
    }
    
    /**
     * Validates that all required services are running.
     * Fails fast if any service is not running.
     * 
     * @param serviceTypes Services to validate
     */
    public void validateServices(ServiceRequirement.ServiceType... serviceTypes) {
        for (ServiceRequirement.ServiceType type : serviceTypes) {
            TestDataValidator.requireServiceRunning(serviceManager, type);
        }
    }
    
    /**
     * Gets HTTP client for a service.
     * 
     * @param serviceType Service type
     * @return HTTP client
     */
    public TestHttpClient getClient(ServiceRequirement.ServiceType serviceType) {
        if (!servicesStarted) {
            throw new IllegalStateException(
                "Services not started. Call startRequiredServices() first.");
        }
        
        return switch (serviceType) {
            case AUTH -> {
                if (authClient == null) {
                    initializeClients();
                }
                yield authClient;
            }
            case CUSTOMER -> {
                if (!serviceManager.isServiceRunning(ServiceRequirement.ServiceType.CUSTOMER)) {
                    throw new IllegalStateException("Customer service is not running");
                }
                yield new TestHttpClient(serviceManager.getCustomerService().getBaseUrl());
            }
            case WALLET -> {
                if (!serviceManager.isServiceRunning(ServiceRequirement.ServiceType.WALLET)) {
                    throw new IllegalStateException("Wallet service is not running");
                }
                yield new TestHttpClient(serviceManager.getWalletService().getBaseUrl());
            }
        };
    }
    
    /**
     * Gets the TestUserManager. Initializes it if not already created.
     * 
     * @return TestUserManager instance
     */
    public TestUserManager getUserManager() {
        if (userManager == null) {
            initializeClients();
        }
        return userManager;
    }
    
    /**
     * Cleans up resources.
     * Stops services and clears test users.
     */
    public void cleanup() {
        log.info("Cleaning up test resources...");
        
        // Stop only the services that were started
        ServiceRequirement annotation = this.getClass().getAnnotation(ServiceRequirement.class);
        if (annotation != null && annotation.value().length > 0) {
            serviceManager.stopRequired(annotation.value());
        } else {
            serviceManager.stopAll();
        }
        
        userManager.clear();
        log.info("✓ Cleanup complete");
    }
}

