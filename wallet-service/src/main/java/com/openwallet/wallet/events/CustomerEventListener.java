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
            // Delegate to service - ensures all business logic is applied
            // including balance caching and proper initialization
            Wallet savedWallet = walletService.createWalletFromEvent(customerId);
            
            log.info("Created wallet: walletId={}, customerId={}, balance={}, dailyLimit={}", 
                    savedWallet.getId(), customerId, savedWallet.getBalance(), savedWallet.getDailyLimit());

            // Publish WALLET_CREATED event
            WalletEvent walletEvent = WalletEvent.builder()
                    .walletId(savedWallet.getId())
                    .customerId(customerId)
                    .userId(userId)
                    .eventType("WALLET_CREATED")
                    .balance(savedWallet.getBalance())
                    .currency(savedWallet.getCurrency())
                    .timestamp(LocalDateTime.now())
                    .build();

            walletEventProducer.publish(walletEvent);
            
            log.info("✓ Wallet created and WALLET_CREATED event published for customerId={}", customerId);

        } catch (IllegalStateException e) {
            // Wallet already exists - this is normal in case of reprocessing
            log.info("Wallet already exists for customerId={}, skipping creation", customerId);
        } catch (Exception e) {
            log.error("Failed to create wallet for customerId={}: {}", customerId, e.getMessage(), e);
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

