package com.openwallet.customer.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.openwallet.customer.domain.Customer;
import com.openwallet.customer.domain.CustomerStatus;
import com.openwallet.customer.dto.UpdateCustomerRequest;
import com.openwallet.customer.repository.CustomerRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc(addFilters = false)
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

    @BeforeEach
    void clean() {
        customerRepository.deleteAll();
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
                .header("X-User-Id", "user-123"))
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
                .header("X-User-Id", "user-456")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
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
                .header("X-User-Id", "missing-user"))
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
                .header("X-User-Id", "user-789")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(invalid)))
                .andExpect(status().isBadRequest());
    }
}
