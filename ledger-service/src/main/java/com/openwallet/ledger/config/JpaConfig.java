package com.openwallet.ledger.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.data.jpa.repository.config.EnableJpaAuditing;

/**
 * Enables JPA auditing for created/updated timestamps.
 */
@Configuration
@EnableJpaAuditing
public class JpaConfig {
}

