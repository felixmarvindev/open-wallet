package com.openwallet.customer.service;

import com.openwallet.customer.domain.Customer;
import com.openwallet.customer.domain.CustomerStatus;
import com.openwallet.customer.domain.CustomerUserMapping;
import com.openwallet.customer.dto.CreateCustomerRequest;
import com.openwallet.customer.dto.CustomerResponse;
import com.openwallet.customer.dto.UpdateCustomerRequest;
import com.openwallet.customer.exception.CustomerNotFoundException;
import com.openwallet.customer.repository.CustomerRepository;
import com.openwallet.customer.repository.CustomerUserMappingRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@SuppressWarnings("null")
public class CustomerService {

    private final CustomerRepository customerRepository;
    private final CustomerUserMappingRepository mappingRepository;

    @Transactional(readOnly = true)
    public CustomerResponse getCurrentCustomer(String userId) {
        Customer customer = customerRepository.findByUserId(userId)
                .orElseThrow(() -> new CustomerNotFoundException("Customer not found"));
        return toResponse(customer);
    }

    /**
     * Creates a new customer profile for the authenticated user.
     *
     * @param userId  Keycloak user ID (from JWT)
     * @param request Customer creation request
     * @return Created customer response
     * @throws IllegalStateException    if customer already exists for this userId
     * @throws IllegalArgumentException if email or phone number already exists
     */
    @Transactional
    public CustomerResponse createCustomer(String userId, CreateCustomerRequest request) {
        // Check if customer already exists for this userId
        if (customerRepository.findByUserId(userId).isPresent()) {
            throw new IllegalStateException("Customer already exists for this user");
        }

        // Check if email already exists
        if (customerRepository.findByEmail(request.getEmail()).isPresent()) {
            throw new IllegalArgumentException("Customer with email '" + request.getEmail() + "' already exists");
        }

        // Check if phone number already exists
        if (customerRepository.findByPhoneNumber(request.getPhoneNumber()).isPresent()) {
            throw new IllegalArgumentException(
                    "Customer with phone number '" + request.getPhoneNumber() + "' already exists");
        }

        // Create customer entity
        Customer customer = Customer.builder()
                .userId(userId)
                .firstName(request.getFirstName())
                .lastName(request.getLastName())
                .phoneNumber(request.getPhoneNumber())
                .email(request.getEmail())
                .address(request.getAddress())
                .status(CustomerStatus.ACTIVE)
                .build();

        Customer saved = customerRepository.save(customer);

        // Ensure mapping exists
        ensureMappingExists(saved);

        return toResponse(saved);
    }

    @Transactional
    public CustomerResponse updateCurrentCustomer(String userId, UpdateCustomerRequest request) {
        Customer customer = customerRepository.findByUserId(userId)
                .orElseThrow(() -> new CustomerNotFoundException("Customer not found"));

        customer.setFirstName(request.getFirstName());
        customer.setLastName(request.getLastName());
        customer.setPhoneNumber(request.getPhoneNumber());
        customer.setEmail(request.getEmail());
        customer.setAddress(request.getAddress());

        Customer saved = customerRepository.save(customer);
        
        // Ensure mapping exists (in case it was missing)
        ensureMappingExists(saved);
        
        return toResponse(saved);
    }
    
    /**
     * Create or update customer-user mapping.
     * Called after customer is saved to ensure mapping is always in sync.
     */
    @Transactional
    public void ensureMappingExists(Customer customer) {
        mappingRepository.findByCustomerId(customer.getId())
                .ifPresent(existing -> {
                    // Update if userId changed (shouldn't happen, but handle it)
                    if (!existing.getUserId().equals(customer.getUserId())) {
                        existing.setUserId(customer.getUserId());
                        mappingRepository.save(existing);
                    }
                });
        
        // Create mapping if missing
        if (mappingRepository.findByCustomerId(customer.getId()).isEmpty()) {
            CustomerUserMapping mapping = CustomerUserMapping.builder()
                    .userId(customer.getUserId())
                    .customerId(customer.getId())
                    .build();
            mappingRepository.save(mapping);
        }
    }

    private CustomerResponse toResponse(Customer customer) {
        return CustomerResponse.builder()
                .id(customer.getId())
                .userId(customer.getUserId())
                .firstName(customer.getFirstName())
                .lastName(customer.getLastName())
                .phoneNumber(customer.getPhoneNumber())
                .email(customer.getEmail())
                .address(customer.getAddress())
                .status(customer.getStatus() != null ? customer.getStatus().name() : null)
                .build();
    }
}


