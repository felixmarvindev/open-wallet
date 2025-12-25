package com.openwallet.wallet.events;

import com.openwallet.wallet.domain.Wallet;
import com.openwallet.wallet.service.WalletService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Listens to customer-events topic and automatically creates wallets.
 * Delegates to WalletService to ensure all business logic is applied.
 */
@Component
@RequiredArgsConstructor
@Slf4j
@SuppressWarnings("null")
public class CustomerEventListener {

    private final WalletService walletService;
    private final WalletEventProducer walletEventProducer;

    private final Set<Long> processedCustomerIds = ConcurrentHashMap.newKeySet();

    @KafkaListener(
            topics = "${app.topics.customer-events:customer-events}",
            groupId = "${spring.kafka.group-id:wallet-service}",
            containerFactory = "customerEventKafkaListenerContainerFactory"
    )
    public void handleCustomerEvent(Map<String, Object> eventData) {
        String eventType = (String) eventData.get("eventType");
        
        if (eventType == null) {
            log.warn("Received event without eventType, ignoring");
            return;
        }

        if ("CUSTOMER_CREATED".equals(eventType)) {
            handleCustomerCreated(eventData);
        }
    }

    private void handleCustomerCreated(Map<String, Object> eventData) {
        Long customerId = getLongValue(eventData.get("customerId"));
        String userId = (String) eventData.get("userId");
        String email = (String) eventData.get("email");

        if (customerId == null) {
            log.warn("CUSTOMER_CREATED event missing customerId, ignoring");
            return;
        }

        // Prevent duplicate processing
        if (!processedCustomerIds.add(customerId)) {
            log.debug("CUSTOMER_CREATED event for customerId={} already processed, skipping", customerId);
            return;
        }

        log.info("Processing CUSTOMER_CREATED event: customerId={}, userId={}", customerId, userId);

        try {
            // Delegate to service - idempotent operation: creates if new, returns existing if already present
            // This ensures safe event reprocessing and handles race conditions gracefully
            Wallet wallet = walletService.createWalletFromEvent(customerId);
            
            log.info("Wallet ensured: walletId={}, customerId={}, balance={}, dailyLimit={}", 
                    wallet.getId(), customerId, wallet.getBalance(), wallet.getDailyLimit());

            // Publish WALLET_CREATED event
            // Note: This may publish duplicate events if the same CUSTOMER_CREATED event is processed
            // multiple times, but downstream services should handle idempotency
            WalletEvent walletEvent = WalletEvent.builder()
                    .walletId(wallet.getId())
                    .customerId(customerId)
                    .userId(userId)
                    .eventType("WALLET_CREATED")
                    .balance(wallet.getBalance())
                    .currency(wallet.getCurrency())
                    .timestamp(LocalDateTime.now())
                    .build();

            walletEventProducer.publish(walletEvent);
            
            log.info("✓ Wallet ensured and WALLET_CREATED event published for customerId={}", customerId);

        } catch (Exception e) {
            log.error("Failed to process wallet for customerId={}: {}", customerId, e.getMessage(), e);
            processedCustomerIds.remove(customerId); // Allow retry
        }
    }

    /**
     * Safely convert Object to Long (handles Integer, Long, String).
     */
    private Long getLongValue(Object value) {
        if (value == null) {
            return null;
        }
        if (value instanceof Long) {
            return (Long) value;
        }
        if (value instanceof Integer) {
            return ((Integer) value).longValue();
        }
        if (value instanceof String) {
            try {
                return Long.parseLong((String) value);
            } catch (NumberFormatException e) {
                return null;
            }
        }
        return null;
    }
}

