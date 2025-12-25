package com.openwallet.customer.service;

import com.openwallet.customer.domain.Customer;
import com.openwallet.customer.domain.CustomerStatus;
import com.openwallet.customer.domain.CustomerUserMapping;
import com.openwallet.customer.dto.CreateCustomerRequest;
import com.openwallet.customer.dto.CustomerResponse;
import com.openwallet.customer.repository.CustomerRepository;
import com.openwallet.customer.repository.CustomerUserMappingRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Integration tests for CustomerService.createCustomer() method.
 */
@SpringBootTest
@ActiveProfiles("test")
@Transactional
@SuppressWarnings("null")
class CustomerServiceCreateTest {

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
    @DisplayName("Create customer should save customer with correct data")
    void createCustomerShouldSaveCustomer() {
        // Given
        String userId = "test-user-123";
        CreateCustomerRequest request = CreateCustomerRequest.builder()
                .firstName("John")
                .lastName("Doe")
                .phoneNumber("+254712345678")
                .email("john.doe@example.com")
                .address("123 Main St")
                .build();

        // When
        CustomerResponse response = customerService.createCustomer(userId, request);

        // Then
        assertThat(response).isNotNull();
        assertThat(response.getId()).isNotNull();
        assertThat(response.getUserId()).isEqualTo(userId);
        assertThat(response.getFirstName()).isEqualTo("John");
        assertThat(response.getLastName()).isEqualTo("Doe");
        assertThat(response.getPhoneNumber()).isEqualTo("+254712345678");
        assertThat(response.getEmail()).isEqualTo("john.doe@example.com");
        assertThat(response.getAddress()).isEqualTo("123 Main St");
        assertThat(response.getStatus()).isEqualTo("ACTIVE");

        // Verify customer was saved in database
        Customer saved = customerRepository.findById(response.getId())
                .orElseThrow(() -> new IllegalStateException("Customer not found"));
        assertThat(saved.getUserId()).isEqualTo(userId);
        assertThat(saved.getStatus()).isEqualTo(CustomerStatus.ACTIVE);
    }

    @Test
    @DisplayName("Create customer should create mapping")
    void createCustomerShouldCreateMapping() {
        // Given
        String userId = "test-user-mapping-123";
        CreateCustomerRequest request = CreateCustomerRequest.builder()
                .firstName("Jane")
                .lastName("Smith")
                .phoneNumber("+254798765432")
                .email("jane.smith@example.com")
                .build();

        // When
        CustomerResponse response = customerService.createCustomer(userId, request);

        // Then: Mapping should be created
        CustomerUserMapping mapping = mappingRepository.findByUserId(userId)
                .orElseThrow(() -> new AssertionError("Mapping not found"));
        assertThat(mapping.getCustomerId()).isEqualTo(response.getId());
        assertThat(mapping.getUserId()).isEqualTo(userId);
    }

    @Test
    @DisplayName("Create customer should update existing customer when customer already exists (upsert)")
    void createCustomerShouldUpdateExistingWhenCustomerExists() {
        // Given: Customer already exists
        String userId = "test-user-duplicate-123";
        Customer existingCustomer = customerRepository.save(Customer.builder()
                .userId(userId)
                .firstName("Existing")
                .lastName("User")
                .phoneNumber("+254711111111")
                .email("existing@example.com")
                .address("Old Address")
                .status(CustomerStatus.ACTIVE)
                .build());

        CreateCustomerRequest request = CreateCustomerRequest.builder()
                .firstName("New")
                .lastName("Updated")
                .phoneNumber("+254722222222")
                .email("new@example.com")
                .address("New Address")
                .build();

        // When: Try to create customer again (upsert)
        CustomerResponse response = customerService.createCustomer(userId, request);

        // Then: Should update existing customer with new data (upsert behavior)
        assertThat(response).isNotNull();
        assertThat(response.getId()).isEqualTo(existingCustomer.getId());
        assertThat(response.getUserId()).isEqualTo(userId);
        assertThat(response.getFirstName()).isEqualTo("New"); // Updated with request data
        assertThat(response.getLastName()).isEqualTo("Updated"); // Updated with request data
        assertThat(response.getEmail()).isEqualTo("new@example.com"); // Updated with request data
        assertThat(response.getPhoneNumber()).isEqualTo("+254722222222"); // Updated with request data
        assertThat(response.getAddress()).isEqualTo("New Address"); // Updated with request data
        assertThat(response.getStatus()).isEqualTo("ACTIVE"); // Status remains unchanged

        // Verify database was updated
        Customer updated = customerRepository.findById(existingCustomer.getId())
                .orElseThrow(() -> new IllegalStateException("Customer not found"));
        assertThat(updated.getFirstName()).isEqualTo("New");
        assertThat(updated.getEmail()).isEqualTo("new@example.com");
    }

    @Test
    @DisplayName("Create customer should throw exception when email already exists")
    void createCustomerShouldThrowExceptionWhenEmailExists() {
        // Given: Customer with email already exists
        customerRepository.save(Customer.builder()
                .userId("existing-user-123")
                .firstName("Existing")
                .lastName("User")
                .phoneNumber("+254711111111")
                .email("duplicate@example.com")
                .status(CustomerStatus.ACTIVE)
                .build());

        CreateCustomerRequest request = CreateCustomerRequest.builder()
                .firstName("New")
                .lastName("User")
                .phoneNumber("+254722222222")
                .email("duplicate@example.com") // Duplicate email
                .build();

        // When/Then
        assertThatThrownBy(() -> customerService.createCustomer("new-user-123", request))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("email")
                .hasMessageContaining("already exists");
    }

    @Test
    @DisplayName("Create customer should throw exception when phone number already exists")
    void createCustomerShouldThrowExceptionWhenPhoneExists() {
        // Given: Customer with phone number already exists
        customerRepository.save(Customer.builder()
                .userId("existing-user-123")
                .firstName("Existing")
                .lastName("User")
                .phoneNumber("+254733333333")
                .email("existing@example.com")
                .status(CustomerStatus.ACTIVE)
                .build());

        CreateCustomerRequest request = CreateCustomerRequest.builder()
                .firstName("New")
                .lastName("User")
                .phoneNumber("+254733333333") // Duplicate phone
                .email("new@example.com")
                .build();

        // When/Then
        assertThatThrownBy(() -> customerService.createCustomer("new-user-123", request))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("phone number")
                .hasMessageContaining("already exists");
    }

    @Test
    @DisplayName("Create customer should set default status to ACTIVE")
    void createCustomerShouldSetDefaultStatus() {
        // Given
        String userId = "test-user-status-123";
        CreateCustomerRequest request = CreateCustomerRequest.builder()
                .firstName("Test")
                .lastName("User")
                .phoneNumber("+254744444444")
                .email("test@example.com")
                .build();

        // When
        CustomerResponse response = customerService.createCustomer(userId, request);

        // Then
        assertThat(response.getStatus()).isEqualTo("ACTIVE");
        Customer saved = customerRepository.findById(response.getId())
                .orElseThrow(() -> new IllegalStateException("Customer not found"));
        assertThat(saved.getStatus()).isEqualTo(CustomerStatus.ACTIVE);
    }
}

