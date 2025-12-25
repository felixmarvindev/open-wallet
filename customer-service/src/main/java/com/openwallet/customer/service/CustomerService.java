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
        // Only update phone number if provided (allows partial updates)
        if (request.getPhoneNumber() != null) {
            customer.setPhoneNumber(request.getPhoneNumber());
        }
        customer.setEmail(request.getEmail());
        // Only update address if provided (allows partial updates)
        if (request.getAddress() != null) {
            customer.setAddress(request.getAddress());
        }

        Customer saved = customerRepository.save(customer);
        
        // Ensure mapping exists (in case it was missing)
        ensureMappingExists(saved);
        
        return toResponse(saved);
    }

    /**
     * Creates or retrieves a customer profile automatically from user registration event.
     * This method is idempotent: if a customer already exists for the userId, it returns
     * the existing customer. This ensures safe event reprocessing and handles race conditions.
     * 
     * Uses partial data (phone number is null) that can be updated later by the user
     * during profile completion. This is used by event listeners to create customer profiles automatically.
     *
     * @param userId   Keycloak user ID
     * @param username Username from registration
     * @param email    Email from registration
     * @return Created or existing customer (idempotent)
     */
    @Transactional
    public Customer createCustomerFromEvent(String userId, String username, String email) {
        // Idempotent: return existing customer if found
        return customerRepository.findByUserId(userId)
                .map(existing -> {
                    // Ensure mapping exists (in case it was missing)
                    ensureMappingExists(existing);
                    return existing;
                })
                .orElseGet(() -> {
                    // Create new customer with partial data (phone number is null until user completes profile)
                    Customer customer = Customer.builder()
                            .userId(userId)
                            .firstName(extractFirstName(username))
                            .lastName(extractLastName(username))
                            .email(email)
                            .phoneNumber(null) // NULL until user provides phone during profile completion
                            .status(CustomerStatus.ACTIVE)
                            .build();

                    Customer saved = customerRepository.save(customer);

                    // CRITICAL: Ensure mapping exists for JWT resolution
                    ensureMappingExists(saved);

                    return saved;
                });
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

    /**
     * Extract first name from username (simple heuristic for auto-creation).
     */
    private String extractFirstName(String username) {
        if (username == null) {
            return "User";
        }
        String[] parts = username.split("_");
        return parts.length > 0 ? capitalize(parts[0]) : "User";
    }

    /**
     * Extract last name from username (simple heuristic for auto-creation).
     */
    private String extractLastName(String username) {
        if (username == null) {
            return "Unknown";
        }
        String[] parts = username.split("_");
        return parts.length > 1 ? parts[1] : "Unknown";
    }

    /**
     * Capitalize first letter of a string.
     */
    private String capitalize(String str) {
        if (str == null || str.isEmpty()) {
            return str;
        }
        return str.substring(0, 1).toUpperCase() + str.substring(1).toLowerCase();
    }
}


