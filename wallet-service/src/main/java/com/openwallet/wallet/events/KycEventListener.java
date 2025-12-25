package com.openwallet.wallet.events;

import com.openwallet.wallet.service.WalletService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Listens to kyc-events topic and updates wallet limits after KYC verification.
 */
@Component
@RequiredArgsConstructor
@Slf4j
@SuppressWarnings("null")
public class KycEventListener {

    private final WalletService walletService;
    private final Set<String> processedKycEvents = ConcurrentHashMap.newKeySet();

    @KafkaListener(
            topics = "${app.topics.kyc-events:kyc-events}",
            groupId = "${spring.kafka.group-id:wallet-service}",
            containerFactory = "kycEventKafkaListenerContainerFactory"
    )
    @Transactional
    public void handleKycEvent(KycEvent event) {
        if (event == null || !"KYC_VERIFIED".equals(event.getEventType())) {
            return;
        }

        String eventKey = event.getKycCheckId() + "-" + event.getEventType();
        if (!processedKycEvents.add(eventKey)) {
            log.debug("Skipping duplicate KYC_VERIFIED event for kycCheckId: {}", event.getKycCheckId());
            return;
        }

        log.info("Processing KYC_VERIFIED event for customerId: {}", event.getCustomerId());

        try {
            walletService.updateLimitsAfterKyc(event.getCustomerId(), event.getStatus());
            log.info("✓ Wallet limits updated after KYC verification for customerId: {}", event.getCustomerId());

        } catch (IllegalStateException e) {
            log.info("Expected error: {}", e.getMessage());
        } catch (Exception e) {
            log.error("Failed to update wallet limits for customerId: {}: {}", event.getCustomerId(), e.getMessage(), e);
            processedKycEvents.remove(eventKey);
            throw e;
        }
    }
}

