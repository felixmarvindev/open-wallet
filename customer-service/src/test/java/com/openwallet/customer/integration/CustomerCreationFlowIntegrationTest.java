package com.openwallet.customer.integration;

import com.openwallet.customer.domain.Customer;
import com.openwallet.customer.domain.CustomerStatus;
import com.openwallet.customer.domain.CustomerUserMapping;
import com.openwallet.customer.dto.CreateCustomerRequest;
import com.openwallet.customer.dto.CustomerResponse;
import com.openwallet.customer.repository.CustomerRepository;
import com.openwallet.customer.repository.CustomerUserMappingRepository;
import com.openwallet.customer.service.CustomerService;
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
 * End-to-end integration test for customer creation flow.
 * Tests: Create Customer → Verify mapping → Get Customer
 */
@SpringBootTest
@ActiveProfiles("test")
@Transactional
@SuppressWarnings("null")
class CustomerCreationFlowIntegrationTest {

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
    @DisplayName("Complete flow: Create Customer → Verify mapping → Get Customer")
    void completeCustomerCreationFlow() {
        // Given
        String userId = "e2e-user-123";
        CreateCustomerRequest request = CreateCustomerRequest.builder()
                .firstName("John")
                .lastName("Doe")
                .phoneNumber("+254712345678")
                .email("john.doe@example.com")
                .address("123 Main St, Nairobi")
                .build();

        // Step 1: Create customer
        CustomerResponse createResponse = customerService.createCustomer(userId, request);

        // Verify customer was created
        assertThat(createResponse).isNotNull();
        assertThat(createResponse.getId()).isNotNull();
        assertThat(createResponse.getUserId()).isEqualTo(userId);
        assertThat(createResponse.getFirstName()).isEqualTo("John");
        assertThat(createResponse.getLastName()).isEqualTo("Doe");
        assertThat(createResponse.getPhoneNumber()).isEqualTo("+254712345678");
        assertThat(createResponse.getEmail()).isEqualTo("john.doe@example.com");
        assertThat(createResponse.getAddress()).isEqualTo("123 Main St, Nairobi");
        assertThat(createResponse.getStatus()).isEqualTo("ACTIVE");

        // Step 2: Verify customer exists in database
        Customer savedCustomer = customerRepository.findById(createResponse.getId())
                .orElseThrow(() -> new IllegalStateException("Customer not found"));
        assertThat(savedCustomer.getUserId()).isEqualTo(userId);
        assertThat(savedCustomer.getStatus()).isEqualTo(CustomerStatus.ACTIVE);

        // Step 3: Verify mapping was created
        CustomerUserMapping mapping = mappingRepository.findByUserId(userId)
                .orElseThrow(() -> new IllegalStateException("Mapping not found"));
        assertThat(mapping.getCustomerId()).isEqualTo(createResponse.getId());
        assertThat(mapping.getUserId()).isEqualTo(userId);

        // Step 4: Get customer using the service
        CustomerResponse getResponse = customerService.getCurrentCustomer(userId);

        // Verify retrieved customer matches created customer
        assertThat(getResponse.getId()).isEqualTo(createResponse.getId());
        assertThat(getResponse.getUserId()).isEqualTo(userId);
        assertThat(getResponse.getFirstName()).isEqualTo("John");
        assertThat(getResponse.getEmail()).isEqualTo("john.doe@example.com");
    }

    @Test
    @DisplayName("Create customer should fail when customer already exists for userId")
    void createCustomerShouldFailWhenCustomerExists() {
        // Given: Customer already exists
        String userId = "e2e-user-duplicate-123";
        customerRepository.save(Customer.builder()
                .userId(userId)
                .firstName("Existing")
                .lastName("User")
                .phoneNumber("+254711111111")
                .email("existing@example.com")
                .status(CustomerStatus.ACTIVE)
                .build());

        CreateCustomerRequest request = CreateCustomerRequest.builder()
                .firstName("New")
                .lastName("User")
                .phoneNumber("+254722222222")
                .email("new@example.com")
                .build();

        // When/Then: Should throw exception
        assertThatThrownBy(() -> customerService.createCustomer(userId, request))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("already exists");
    }

    @Test
    @DisplayName("Create customer should fail when email already exists")
    void createCustomerShouldFailWhenEmailExists() {
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

        // When/Then: Should throw exception
        assertThatThrownBy(() -> customerService.createCustomer("new-user-123", request))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("email")
                .hasMessageContaining("already exists");
    }
}

