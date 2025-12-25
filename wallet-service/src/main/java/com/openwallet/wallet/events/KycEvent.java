package com.openwallet.wallet.events;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;
import java.util.Map;

/**
 * KYC event DTO for wallet-service.
 * Mirrors the event published by customer-service.
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class KycEvent {
    private Long kycCheckId;
    private Long customerId;
    private String userId;
    private String eventType; // KYC_INITIATED, KYC_VERIFIED, KYC_REJECTED
    private String status;
    private String providerReference;
    private LocalDateTime initiatedAt;
    private LocalDateTime verifiedAt;
    private String rejectionReason;
    private Map<String, Object> documents;
}



