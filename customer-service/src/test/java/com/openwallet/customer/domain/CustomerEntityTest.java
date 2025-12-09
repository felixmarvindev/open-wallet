package com.openwallet.customer.domain;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.autoconfigure.orm.jpa.TestEntityManager;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;
import com.openwallet.customer.config.JpaConfig;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Integration test for Customer entity to verify JPA mapping and persistence.
 */
@DataJpaTest
@Import(JpaConfig.class)
@ActiveProfiles("test")
class CustomerEntityTest {

    @Autowired
    private TestEntityManager entityManager;

    @Test
    void shouldPersistCustomerWithAllFields() {
        // Given
        Customer customer = Customer.builder()
                .userId("keycloak-user-123")
                .firstName("John")
                .lastName("Doe")
                .phoneNumber("+254712345678")
                .email("john.doe@example.com")
                .address("123 Main St, Nairobi")
                .status(CustomerStatus.ACTIVE)
                .build();

        // When
        Customer saved = entityManager.persistAndFlush(customer);

        // Then
        assertThat(saved.getId()).isNotNull();
        assertThat(saved.getUserId()).isEqualTo("keycloak-user-123");
        assertThat(saved.getFirstName()).isEqualTo("John");
        assertThat(saved.getLastName()).isEqualTo("Doe");
        assertThat(saved.getPhoneNumber()).isEqualTo("+254712345678");
        assertThat(saved.getEmail()).isEqualTo("john.doe@example.com");
        assertThat(saved.getStatus()).isEqualTo(CustomerStatus.ACTIVE);
        assertThat(saved.getCreatedAt()).isNotNull();
        assertThat(saved.getUpdatedAt()).isNotNull();
    }

    @Test
    void shouldSetDefaultStatusToActive() {
        // Given
        Customer customer = Customer.builder()
                .userId("keycloak-user-456")
                .firstName("Jane")
                .lastName("Smith")
                .phoneNumber("+254798765432")
                .email("jane.smith@example.com")
                .build();

        // When
        Customer saved = entityManager.persistAndFlush(customer);

        // Then
        assertThat(saved.getStatus()).isEqualTo(CustomerStatus.ACTIVE);
    }

    @Test
    void shouldMaintainRelationshipWithKycCheck() {
        // Given
        Customer customer = Customer.builder()
                .userId("keycloak-user-789")
                .firstName("Test")
                .lastName("User")
                .phoneNumber("+254700000000")
                .email("test@example.com")
                .build();

        Customer savedCustomer = entityManager.persistAndFlush(customer);

        KycCheck kycCheck = KycCheck.builder()
                .customer(savedCustomer)
                .status(KycStatus.PENDING)
                .build();

        // When
        KycCheck savedKyc = entityManager.persistAndFlush(kycCheck);

        // Then
        assertThat(savedKyc.getId()).isNotNull();
        assertThat(savedKyc.getCustomer().getId()).isEqualTo(savedCustomer.getId());
        assertThat(savedKyc.getStatus()).isEqualTo(KycStatus.PENDING);
    }
}


