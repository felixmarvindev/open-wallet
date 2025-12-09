package com.openwallet.customer.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.openwallet.customer.domain.Customer;
import com.openwallet.customer.domain.CustomerStatus;
import com.openwallet.customer.domain.KycCheck;
import com.openwallet.customer.domain.KycStatus;
import com.openwallet.customer.dto.KycInitiateRequest;
import com.openwallet.customer.dto.KycWebhookRequest;
import com.openwallet.customer.events.KycEventProducer;
import com.openwallet.customer.repository.CustomerRepository;
import com.openwallet.customer.repository.KycCheckRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.Collections;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
@SuppressWarnings("null")
class KycControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private CustomerRepository customerRepository;

    @Autowired
    private KycCheckRepository kycCheckRepository;

    @MockBean
    private KycEventProducer kycEventProducer;

    @BeforeEach
    void clean() {
        kycCheckRepository.deleteAll();
        customerRepository.deleteAll();
    }

    @Test
    void initiateKycCreatesRecordAndPublishesEvent() throws Exception {
        Customer customer = customerRepository.save(Customer.builder()
                .userId("user-kyc-1")
                .firstName("Kyc")
                .lastName("User")
                .phoneNumber("+254700000010")
                .email("kyc.user@example.com")
                .status(CustomerStatus.ACTIVE)
                .build());

        KycInitiateRequest request = KycInitiateRequest.builder()
                .documents(java.util.Collections.singletonMap("idFront", "base64data"))
                .build();

        mockMvc.perform(post("/api/v1/customers/me/kyc/initiate")
                        .header("X-User-Id", "user-kyc-1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request))
                        .with(SecurityMockMvcRequestPostProcessors.jwt()
                                .jwt(jwt -> jwt.subject("user-kyc-1")
                                        .claim("realm_access", Collections.singletonMap("roles", Arrays.asList("USER"))))
                                .authorities(new SimpleGrantedAuthority("ROLE_USER"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("IN_PROGRESS"));

        assertThat(kycCheckRepository.findTopByCustomerIdOrderByCreatedAtDesc(customer.getId())).isPresent();
        verify(kycEventProducer, times(1)).publish(org.mockito.Mockito.any());
    }

    @Test
    void getKycStatusReturnsLatest() throws Exception {
        Customer customer = customerRepository.save(Customer.builder()
                .userId("user-kyc-2")
                .firstName("Kyc")
                .lastName("Status")
                .phoneNumber("+254700000011")
                .email("kyc.status@example.com")
                .status(CustomerStatus.ACTIVE)
                .build());

        kycCheckRepository.save(KycCheck.builder()
                .customer(customer)
                .status(KycStatus.IN_PROGRESS)
                .providerReference("ref-1")
                .build());

        mockMvc.perform(get("/api/v1/customers/me/kyc/status")
                        .header("X-User-Id", "user-kyc-2")
                        .with(SecurityMockMvcRequestPostProcessors.jwt()
                                .jwt(jwt -> jwt.subject("user-kyc-2")
                                        .claim("realm_access", Collections.singletonMap("roles", Arrays.asList("USER"))))
                                .authorities(new SimpleGrantedAuthority("ROLE_USER"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("IN_PROGRESS"));
    }

    @Test
    void webhookVerifiesKyc() throws Exception {
        Customer customer = customerRepository.save(Customer.builder()
                .userId("user-kyc-3")
                .firstName("Webhook")
                .lastName("User")
                .phoneNumber("+254700000012")
                .email("webhook.user@example.com")
                .status(CustomerStatus.ACTIVE)
                .build());

        KycCheck kycCheck = kycCheckRepository.save(KycCheck.builder()
                .customer(customer)
                .status(KycStatus.IN_PROGRESS)
                .providerReference("ref-web")
                .initiatedAt(LocalDateTime.now())
                .build());

        KycWebhookRequest request = KycWebhookRequest.builder()
                .providerReference("ref-web")
                .status("VERIFIED")
                .customerId(customer.getId())
                .verifiedAt(LocalDateTime.now().toString())
                .build();

        mockMvc.perform(post("/api/v1/customers/kyc/webhook")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("VERIFIED"));

        KycCheck updated = kycCheckRepository.findById(kycCheck.getId())
                .orElseThrow(() -> new IllegalStateException("KYC not found"));
        assertThat(updated.getStatus()).isEqualTo(KycStatus.VERIFIED);
        verify(kycEventProducer, times(1)).publish(org.mockito.Mockito.any());
    }

    @Test
    void initiateKycBlocksIfAlreadyInProgress() throws Exception {
        Customer customer = customerRepository.save(Customer.builder()
                .userId("user-kyc-4")
                .firstName("Blocked")
                .lastName("User")
                .phoneNumber("+254700000013")
                .email("blocked.user@example.com")
                .status(CustomerStatus.ACTIVE)
                .build());

        kycCheckRepository.save(KycCheck.builder()
                .customer(customer)
                .status(KycStatus.IN_PROGRESS)
                .build());

        KycInitiateRequest request = KycInitiateRequest.builder()
                .documents(java.util.Collections.singletonMap("idFront", "data"))
                .build();

        mockMvc.perform(post("/api/v1/customers/me/kyc/initiate")
                        .header("X-User-Id", "user-kyc-4")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request))
                        .with(SecurityMockMvcRequestPostProcessors.jwt()
                                .jwt(jwt -> jwt.subject("user-kyc-4")
                                        .claim("realm_access", Collections.singletonMap("roles", Arrays.asList("USER"))))
                                .authorities(new SimpleGrantedAuthority("ROLE_USER"))))
                .andExpect(status().isBadRequest());
    }
}


