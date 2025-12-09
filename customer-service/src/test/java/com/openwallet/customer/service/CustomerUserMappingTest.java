package com.openwallet.customer.service;

import com.openwallet.customer.domain.Customer;
import com.openwallet.customer.domain.CustomerStatus;
import com.openwallet.customer.domain.CustomerUserMapping;
import com.openwallet.customer.dto.UpdateCustomerRequest;
import com.openwallet.customer.repository.CustomerRepository;
import com.openwallet.customer.repository.CustomerUserMappingRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Integration test to verify customer-user mapping is auto-populated
 * when customers are created or updated.
 */
@SpringBootTest
@ActiveProfiles("test")
@Transactional
@SuppressWarnings("null")
class CustomerUserMappingTest {

    @Autowired
    private CustomerService customerService;

    @Autowired
    private CustomerRepository customerRepository;

    @Autowired
    private CustomerUserMappingRepository mappingRepository;

    @BeforeEach
    void clean() {
        mappingRepository.deleteAll();
        customerRepository.deleteAll();
    }

    @Test
    void updateCustomerShouldCreateMapping() {
        // Given: A customer exists
        Customer customer = customerRepository.save(Customer.builder()
                .userId("test-user-123")
                .firstName("Test")
                .lastName("User")
                .phoneNumber("+254700000001")
                .email("test.user@example.com")
                .status(CustomerStatus.ACTIVE)
                .build());

        // When: Customer is updated via service
        UpdateCustomerRequest request = UpdateCustomerRequest.builder()
                .firstName("Updated")
                .lastName("Name")
                .phoneNumber("+254700000002")
                .email("updated@example.com")
                .address("New Address")
                .build();
        customerService.updateCurrentCustomer("test-user-123", request);

        // Then: Mapping should be created
        CustomerUserMapping mapping = mappingRepository.findByUserId("test-user-123")
                .orElseThrow(() -> new AssertionError("Mapping not found"));
        assertThat(mapping.getCustomerId()).isEqualTo(customer.getId());
        assertThat(mapping.getUserId()).isEqualTo("test-user-123");
    }

    @Test
    void updateCustomerShouldUpdateExistingMapping() {
        // Given: Customer and mapping exist
        Customer customer = customerRepository.save(Customer.builder()
                .userId("test-user-456")
                .firstName("Test")
                .lastName("User")
                .phoneNumber("+254700000003")
                .email("test2@example.com")
                .status(CustomerStatus.ACTIVE)
                .build());

        // Create mapping manually first
        CustomerUserMapping existingMapping = CustomerUserMapping.builder()
                .userId("test-user-456")
                .customerId(customer.getId())
                .build();
        mappingRepository.save(existingMapping);

        // When: Customer is updated
        UpdateCustomerRequest request = UpdateCustomerRequest.builder()
                .firstName("Updated")
                .lastName("Name")
                .phoneNumber("+254700000004")
                .email("updated2@example.com")
                .build();
        customerService.updateCurrentCustomer("test-user-456", request);

        // Then: Mapping should still exist and be valid
        CustomerUserMapping mapping = mappingRepository.findByUserId("test-user-456")
                .orElseThrow(() -> new AssertionError("Mapping not found"));
        assertThat(mapping.getCustomerId()).isEqualTo(customer.getId());
    }
}

