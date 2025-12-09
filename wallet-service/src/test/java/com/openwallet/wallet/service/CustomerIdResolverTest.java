package com.openwallet.wallet.service;

import com.openwallet.wallet.domain.CustomerUserMapping;
import com.openwallet.wallet.repository.CustomerUserMappingRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Integration test to verify CustomerIdResolver can resolve customerId from userId.
 */
@SpringBootTest
@ActiveProfiles("test")
@Transactional
@SuppressWarnings("null")
class CustomerIdResolverTest {

    @Autowired
    private CustomerIdResolver customerIdResolver;

    @Autowired
    private CustomerUserMappingRepository mappingRepository;

    @BeforeEach
    void clean() {
        mappingRepository.deleteAll();
    }

    @Test
    void resolveCustomerIdShouldReturnCustomerId() {
        // Given: Mapping exists
        mappingRepository.save(CustomerUserMapping.builder()
                .userId("test-user-789")
                .customerId(100L)
                .build());

        // When: Resolving customerId
        Long customerId = customerIdResolver.resolveCustomerId("test-user-789");

        // Then: Should return correct customerId
        assertThat(customerId).isEqualTo(100L);
    }

    @Test
    void resolveCustomerIdShouldThrowWhenNotFound() {
        // When/Then: Resolving non-existent userId should throw
        assertThatThrownBy(() -> customerIdResolver.resolveCustomerId("non-existent-user"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Customer not found for user ID");
    }
}

