package com.openwallet.ledger.config;

import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;

/**
 * Test configuration for Clock bean.
 * Provides a fixed clock that can be set to specific dates for testing.
 * 
 * Usage in tests:
 * <pre>
 * {@code
 * @TestConfiguration
 * static class TestConfig {
 *     @Bean
 *     @Primary
 *     Clock testClock() {
 *         return Clock.fixed(Instant.parse("2025-01-15T10:00:00Z"), ZoneId.systemDefault());
 *     }
 * }
 * }
 * </pre>
 */
@TestConfiguration
public class TestClockConfig {

    /**
     * Default test clock - uses current time.
     * Tests can override this by providing their own @Primary Clock bean.
     */
    @Bean
    @Primary
    public Clock testClock() {
        return Clock.systemDefaultZone();
    }
}

