package com.openwallet.wallet.repository;

import com.openwallet.wallet.domain.CustomerUserMapping;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface CustomerUserMappingRepository extends JpaRepository<CustomerUserMapping, String> {
    Optional<CustomerUserMapping> findByUserId(String userId);
}

