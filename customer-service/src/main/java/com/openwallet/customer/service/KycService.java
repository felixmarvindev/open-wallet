package com.openwallet.customer.service;

import com.openwallet.customer.domain.Customer;
import com.openwallet.customer.domain.KycCheck;
import com.openwallet.customer.domain.KycStatus;
import com.openwallet.customer.dto.KycInitiateRequest;
import com.openwallet.customer.dto.KycStatusResponse;
import com.openwallet.customer.dto.KycWebhookRequest;
import com.openwallet.customer.events.KycEvent;
import com.openwallet.customer.events.KycEventProducer;
import com.openwallet.customer.exception.CustomerNotFoundException;
import com.openwallet.customer.repository.CustomerRepository;
import com.openwallet.customer.repository.KycCheckRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@SuppressWarnings("null")
public class KycService {

    private final CustomerRepository customerRepository;
    private final KycCheckRepository kycCheckRepository;
    private final KycEventProducer kycEventProducer;

    @Transactional
    public KycStatusResponse initiateKyc(String userId, KycInitiateRequest request) {
        Customer customer = findCustomer(userId);

        kycCheckRepository.findTopByCustomerIdAndStatusOrderByCreatedAtDesc(customer.getId(), KycStatus.IN_PROGRESS)
                .ifPresent(existing -> {
                    throw new IllegalStateException("KYC already in progress");
                });

        KycCheck kycCheck = KycCheck.builder()
                .customer(customer)
                .status(KycStatus.IN_PROGRESS)
                .providerReference("KYC-" + UUID.randomUUID())
                .documents(request.getDocuments())
                .initiatedAt(LocalDateTime.now())
                .build();

        KycCheck saved = kycCheckRepository.save(kycCheck);
        publishEvent(saved, customer, "KYC_INITIATED");

        return toResponse(saved);
    }

    @Transactional(readOnly = true)
    public KycStatusResponse getKycStatus(String userId) {
        Customer customer = findCustomer(userId);
        return kycCheckRepository.findTopByCustomerIdOrderByCreatedAtDesc(customer.getId())
                .map(this::toResponse)
                .orElse(KycStatusResponse.builder().status(KycStatus.PENDING.name()).build());
    }

    @Transactional
    public KycStatusResponse handleWebhook(KycWebhookRequest request) {
        KycCheck kycCheck = kycCheckRepository.findTopByCustomerIdOrderByCreatedAtDesc(request.getCustomerId())
                .orElseThrow(() -> new IllegalStateException("No KYC record found for customer"));

        if (kycCheck.getStatus() == KycStatus.VERIFIED || kycCheck.getStatus() == KycStatus.REJECTED) {
            throw new IllegalStateException("KYC already completed");
        }

        String status = request.getStatus().toUpperCase();
        if ("VERIFIED".equals(status)) {
            kycCheck.setStatus(KycStatus.VERIFIED);
            kycCheck.setVerifiedAt(parseOrNow(request.getVerifiedAt()));
            kycCheck.setRejectionReason(null);
            publishEvent(kycCheck, kycCheck.getCustomer(), "KYC_VERIFIED");
        } else if ("REJECTED".equals(status)) {
            kycCheck.setStatus(KycStatus.REJECTED);
            kycCheck.setRejectionReason(request.getRejectionReason());
            kycCheck.setVerifiedAt(parseOrNow(request.getVerifiedAt()));
            publishEvent(kycCheck, kycCheck.getCustomer(), "KYC_REJECTED");
        } else {
            throw new IllegalArgumentException("Unsupported status: " + request.getStatus());
        }

        KycCheck saved = kycCheckRepository.save(kycCheck);
        return toResponse(saved);
    }

    private KycStatusResponse toResponse(KycCheck kycCheck) {
        return KycStatusResponse.builder()
                .status(kycCheck.getStatus().name())
                .verifiedAt(kycCheck.getVerifiedAt() != null ? kycCheck.getVerifiedAt().toString() : null)
                .rejectionReason(kycCheck.getRejectionReason())
                .build();
    }

    private Customer findCustomer(String userId) {
        return customerRepository.findByUserId(userId)
                .orElseThrow(() -> new CustomerNotFoundException("Customer not found"));
    }

    private void publishEvent(KycCheck kycCheck, Customer customer, String eventType) {
        KycEvent event = KycEvent.builder()
                .kycCheckId(kycCheck.getId())
                .customerId(customer.getId())
                .userId(customer.getUserId())
                .eventType(eventType)
                .status(kycCheck.getStatus().name())
                .providerReference(kycCheck.getProviderReference())
                .initiatedAt(kycCheck.getInitiatedAt())
                .verifiedAt(kycCheck.getVerifiedAt())
                .rejectionReason(kycCheck.getRejectionReason())
                .documents(kycCheck.getDocuments())
                .build();
        kycEventProducer.publish(event);
    }

    private LocalDateTime parseOrNow(String value) {
        if (StringUtils.hasText(value)) {
            try {
                return LocalDateTime.parse(value);
            } catch (Exception ignored) {
                return LocalDateTime.now();
            }
        }
        return LocalDateTime.now();
    }
}


