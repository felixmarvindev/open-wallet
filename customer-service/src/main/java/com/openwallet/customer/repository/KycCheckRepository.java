package com.openwallet.customer.repository;

import com.openwallet.customer.domain.KycCheck;
import com.openwallet.customer.domain.KycStatus;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface KycCheckRepository extends JpaRepository<KycCheck, Long> {
    Optional<KycCheck> findTopByCustomerIdOrderByCreatedAtDesc(Long customerId);

    Optional<KycCheck> findTopByCustomerIdAndStatusOrderByCreatedAtDesc(Long customerId, KycStatus status);
}
