package com.openwallet.integration.infrastructure;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Annotation to specify which services are required for a test class or method.
 * This allows selective service startup, reducing test execution time.
 * 
 * Usage:
 * <pre>
 * {@code
 * @ServiceRequirement({ServiceType.AUTH, ServiceType.CUSTOMER})
 * class MyTest extends IntegrationTestBase {
 *     // Only auth and customer services will be started
 * }
 * }
 * </pre>
 * 
 * If not specified, all services are started (backward compatibility).
 */
@Target({ElementType.TYPE, ElementType.METHOD})
@Retention(RetentionPolicy.RUNTIME)
public @interface ServiceRequirement {
    /**
     * Services required for this test.
     * 
     * @return Array of required service types
     */
    ServiceType[] value() default {ServiceType.AUTH, ServiceType.CUSTOMER, ServiceType.WALLET};
    
    /**
     * Enumeration of available service types.
     */
    enum ServiceType {
        AUTH,
        CUSTOMER,
        WALLET,
        LEDGER
    }
}

