package com.openwallet.customer.repository;

import com.openwallet.customer.domain.CustomerUserMapping;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface CustomerUserMappingRepository extends JpaRepository<CustomerUserMapping, String> {
    Optional<CustomerUserMapping> findByUserId(String userId);
    
    Optional<CustomerUserMapping> findByCustomerId(Long customerId);
    
    void deleteByCustomerId(Long customerId);
}

