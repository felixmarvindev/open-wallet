package com.openwallet.ledger.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.data.jpa.repository.config.EnableJpaAuditing;

/**
 * JPA configuration to enable auditing features like @CreatedDate and @LastModifiedDate.
 */
@Configuration
@EnableJpaAuditing
public class JpaConfig {
}

