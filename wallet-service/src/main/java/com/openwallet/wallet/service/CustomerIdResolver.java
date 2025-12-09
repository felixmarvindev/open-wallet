package com.openwallet.wallet.service;

import com.openwallet.wallet.domain.CustomerUserMapping;
import com.openwallet.wallet.repository.CustomerUserMappingRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Service to resolve customerId from userId (JWT sub claim).
 * Uses the customer_user_mapping table for fast lookups.
 */
@Service
@RequiredArgsConstructor
public class CustomerIdResolver {

    private final CustomerUserMappingRepository mappingRepository;

    @Transactional(readOnly = true)
    public Long resolveCustomerId(String userId) {
        return mappingRepository.findByUserId(userId)
                .map(CustomerUserMapping::getCustomerId)
                .orElseThrow(() -> new IllegalArgumentException("Customer not found for user ID: " + userId));
    }
}

