package com.openwallet.customer.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.openwallet.customer.domain.Customer;
import com.openwallet.customer.domain.CustomerStatus;
import com.openwallet.customer.dto.CreateCustomerRequest;
import com.openwallet.customer.dto.UpdateCustomerRequest;
import com.openwallet.customer.repository.CustomerRepository;
import com.openwallet.customer.repository.CustomerUserMappingRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import java.util.Arrays;
import java.util.Collections;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
@SuppressWarnings("null")
class CustomerControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

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
    void createCustomerShouldReturn201Created() throws Exception {
        CreateCustomerRequest request = CreateCustomerRequest.builder()
                .firstName("John")
                .lastName("Doe")
                .phoneNumber("+254712345678")
                .email("john.doe@example.com")
                .address("123 Main St")
                .build();

        mockMvc.perform(post("/api/v1/customers")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request))
                        .with(SecurityMockMvcRequestPostProcessors.jwt()
                                .jwt(jwt -> jwt.subject("user-new-123")
                                        .claim("realm_access",
                                                Collections.singletonMap("roles",
                                                        Arrays.asList("USER"))))
                                .authorities(new SimpleGrantedAuthority("ROLE_USER"))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").exists())
                .andExpect(jsonPath("$.userId").value("user-new-123"))
                .andExpect(jsonPath("$.firstName").value("John"))
                .andExpect(jsonPath("$.lastName").value("Doe"))
                .andExpect(jsonPath("$.phoneNumber").value("+254712345678"))
                .andExpect(jsonPath("$.email").value("john.doe@example.com"))
                .andExpect(jsonPath("$.address").value("123 Main St"))
                .andExpect(jsonPath("$.status").value("ACTIVE"));

        // Verify customer was saved
        Customer saved = customerRepository.findByUserId("user-new-123")
                .orElseThrow(() -> new IllegalStateException("Customer not found"));
        assertThat(saved.getFirstName()).isEqualTo("John");
        assertThat(saved.getEmail()).isEqualTo("john.doe@example.com");
    }

    @Test
    void createCustomerShouldCreateMapping() throws Exception {
        CreateCustomerRequest request = CreateCustomerRequest.builder()
                .firstName("Jane")
                .lastName("Smith")
                .phoneNumber("+254798765432")
                .email("jane.smith@example.com")
                .address("456 Oak Ave")
                .build();

        mockMvc.perform(post("/api/v1/customers")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request))
                        .with(SecurityMockMvcRequestPostProcessors.jwt()
                                .jwt(jwt -> jwt.subject("user-mapping-123")
                                        .claim("realm_access",
                                                Collections.singletonMap("roles",
                                                        Arrays.asList("USER"))))
                                .authorities(new SimpleGrantedAuthority("ROLE_USER"))))
                .andExpect(status().isCreated());

        // Verify mapping was created
        assertThat(mappingRepository.findByUserId("user-mapping-123")).isPresent();
        Customer customer = customerRepository.findByUserId("user-mapping-123")
                .orElseThrow(() -> new IllegalStateException("Customer not found"));
        assertThat(mappingRepository.findByUserId("user-mapping-123").get().getCustomerId())
                .isEqualTo(customer.getId());
    }

    @Test
    void createCustomerShouldReturn400WhenCustomerAlreadyExists() throws Exception {
        // Given: Customer already exists
        customerRepository.save(Customer.builder()
                .userId("user-duplicate-123")
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

        // When/Then: Should return 400 Bad Request
        mockMvc.perform(post("/api/v1/customers")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request))
                        .with(SecurityMockMvcRequestPostProcessors.jwt()
                                .jwt(jwt -> jwt.subject("user-duplicate-123")
                                        .claim("realm_access",
                                                Collections.singletonMap("roles",
                                                        Arrays.asList("USER"))))
                                .authorities(new SimpleGrantedAuthority("ROLE_USER"))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value(org.hamcrest.Matchers.containsString("already exists")));
    }

    @Test
    void createCustomerShouldReturn400WhenEmailAlreadyExists() throws Exception {
        // Given: Customer with email already exists
        customerRepository.save(Customer.builder()
                .userId("user-email-123")
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

        // When/Then: Should return 400 Bad Request
        mockMvc.perform(post("/api/v1/customers")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request))
                        .with(SecurityMockMvcRequestPostProcessors.jwt()
                                .jwt(jwt -> jwt.subject("user-new-email-123")
                                        .claim("realm_access",
                                                Collections.singletonMap("roles",
                                                        Arrays.asList("USER"))))
                                .authorities(new SimpleGrantedAuthority("ROLE_USER"))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value(org.hamcrest.Matchers.containsString("email")));
    }

    @Test
    void createCustomerShouldReturn400WhenPhoneNumberAlreadyExists() throws Exception {
        // Given: Customer with phone number already exists
        customerRepository.save(Customer.builder()
                .userId("user-phone-123")
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

        // When/Then: Should return 400 Bad Request
        mockMvc.perform(post("/api/v1/customers")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request))
                        .with(SecurityMockMvcRequestPostProcessors.jwt()
                                .jwt(jwt -> jwt.subject("user-new-phone-123")
                                        .claim("realm_access",
                                                Collections.singletonMap("roles",
                                                        Arrays.asList("USER"))))
                                .authorities(new SimpleGrantedAuthority("ROLE_USER"))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value(org.hamcrest.Matchers.containsString("phone number")));
    }

    @Test
    void getMyProfileReturnsCustomer() throws Exception {
        Customer customer = customerRepository.save(Customer.builder()
                .userId("user-123")
                .firstName("John")
                .lastName("Doe")
                .phoneNumber("+254712345678")
                .email("john.doe@example.com")
                .address("123 Main St")
                .status(CustomerStatus.ACTIVE)
                .build());

        mockMvc.perform(get("/api/v1/customers/me")
                        .with(SecurityMockMvcRequestPostProcessors.jwt()
                                        .jwt(jwt -> jwt.subject("user-123")
                                                        .claim("realm_access",
                                                                        Collections.singletonMap("roles",
                                                                                        Arrays.asList("USER"))))
                                        .authorities(new SimpleGrantedAuthority("ROLE_USER"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(customer.getId()))
                .andExpect(jsonPath("$.firstName").value("John"))
                .andExpect(jsonPath("$.email").value("john.doe@example.com"))
                .andExpect(jsonPath("$.status").value("ACTIVE"));
    }

    @Test
    void updateMyProfileUpdatesCustomer() throws Exception {
        Customer customer = customerRepository.save(Customer.builder()
                .userId("user-456")
                .firstName("Jane")
                .lastName("Smith")
                .phoneNumber("+254798765432")
                .email("jane.smith@example.com")
                .address("Old Address")
                .status(CustomerStatus.ACTIVE)
                .build());

        UpdateCustomerRequest request = UpdateCustomerRequest.builder()
                .firstName("Janet")
                .lastName("Smithers")
                .phoneNumber("+254700000001")
                .email("janet.smithers@example.com")
                .address("New Address")
                .build();

        mockMvc.perform(put("/api/v1/customers/me")
                .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request))
                        .with(SecurityMockMvcRequestPostProcessors.jwt()
                                        .jwt(jwt -> jwt.subject("user-456")
                                                        .claim("realm_access",
                                                                        Collections.singletonMap("roles",
                                                                                        Arrays.asList("USER"))))
                                        .authorities(new SimpleGrantedAuthority("ROLE_USER"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.firstName").value("Janet"))
                .andExpect(jsonPath("$.lastName").value("Smithers"))
                .andExpect(jsonPath("$.phoneNumber").value("+254700000001"))
                .andExpect(jsonPath("$.email").value("janet.smithers@example.com"))
                .andExpect(jsonPath("$.address").value("New Address"));

        Customer updated = customerRepository.findById(customer.getId())
                .orElseThrow(() -> new IllegalStateException("Customer not found in test"));
        assertThat(updated.getFirstName()).isEqualTo("Janet");
        assertThat(updated.getLastName()).isEqualTo("Smithers");
        assertThat(updated.getPhoneNumber()).isEqualTo("+254700000001");
        assertThat(updated.getEmail()).isEqualTo("janet.smithers@example.com");
        assertThat(updated.getAddress()).isEqualTo("New Address");
    }

    @Test
    void getMyProfileReturns404WhenMissing() throws Exception {
        mockMvc.perform(get("/api/v1/customers/me")
                        .with(SecurityMockMvcRequestPostProcessors.jwt()
                                        .jwt(jwt -> jwt.subject("missing-user")
                                                        .claim("realm_access",
                                                                        Collections.singletonMap("roles",
                                                                                        Arrays.asList("USER"))))
                                        .authorities(new SimpleGrantedAuthority("ROLE_USER"))))
                .andExpect(status().isNotFound());
    }

    @Test
    void updateMyProfileValidatesRequest() throws Exception {
        UpdateCustomerRequest invalid = UpdateCustomerRequest.builder()
                .firstName("") // not blank
                .lastName("") // not blank
                .phoneNumber("12345") // invalid format
                .email("not-an-email")
                .address("A")
                .build();

        mockMvc.perform(put("/api/v1/customers/me")
                .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(invalid))
                        .with(SecurityMockMvcRequestPostProcessors.jwt()
                                        .jwt(jwt -> jwt.subject("user-789")
                                                        .claim("realm_access",
                                                                        Collections.singletonMap("roles",
                                                                                        Arrays.asList("USER"))))
                                        .authorities(new SimpleGrantedAuthority("ROLE_USER"))))
                .andExpect(status().isBadRequest());
    }
}
