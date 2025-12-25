package com.openwallet.customer.listener;

import com.openwallet.customer.domain.Customer;
import com.openwallet.customer.events.CustomerEvent;
import com.openwallet.customer.events.CustomerEventProducer;
import com.openwallet.customer.service.CustomerService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Listens to user-events topic and automatically creates customer profiles.
 * Delegates to CustomerService to ensure all business logic is applied.
 */
@Component
@RequiredArgsConstructor
@Slf4j
@SuppressWarnings("null")
public class UserEventListener {

    private final CustomerService customerService;
    private final CustomerEventProducer customerEventProducer;

    private final Set<String> processedUserIds = ConcurrentHashMap.newKeySet();

    @KafkaListener(
            topics = "${app.topics.user-events:user-events}",
            groupId = "${spring.kafka.group-id:customer-service}",
            containerFactory = "userEventKafkaListenerContainerFactory"
    )
    public void handleUserEvent(Map<String, Object> eventData) {
        String eventType = (String) eventData.get("eventType");
        
        if (eventType == null) {
            log.warn("Received event without eventType, ignoring");
            return;
        }

        if ("USER_REGISTERED".equals(eventType)) {
            handleUserRegistered(eventData);
        }
    }

    private void handleUserRegistered(Map<String, Object> eventData) {
        String userId = (String) eventData.get("userId");
        String username = (String) eventData.get("username");
        String email = (String) eventData.get("email");

        if (userId == null || email == null) {
            log.warn("USER_REGISTERED event missing required fields, ignoring");
            return;
        }

        // Prevent duplicate processing
        if (!processedUserIds.add(userId)) {
            log.debug("USER_REGISTERED event for userId={} already processed, skipping", userId);
            return;
        }

        log.info("Processing USER_REGISTERED event: userId={}, username={}, email={}", 
                userId, username, email);

        try {
            // Delegate to service - idempotent operation: creates if new, returns existing if already present
            // This ensures safe event reprocessing and handles race conditions gracefully
            Customer customer = customerService.createCustomerFromEvent(userId, username, email);

            log.info("Customer profile ensured: customerId={}, userId={}, phoneNumber={}", 
                    customer.getId(), userId, customer.getPhoneNumber() != null ? "set" : "null");

            // Publish CUSTOMER_CREATED event
            // Note: This may publish duplicate events if the same USER_REGISTERED event is processed
            // multiple times, but downstream services should handle idempotency
            CustomerEvent customerEvent = CustomerEvent.builder()
                    .customerId(customer.getId())
                    .userId(userId)
                    .email(email)
                    .eventType("CUSTOMER_CREATED")
                    .timestamp(LocalDateTime.now())
                    .build();

            customerEventProducer.publish(customerEvent);
            
            log.info("✓ Customer ensured and CUSTOMER_CREATED event published for userId={}", userId);

        } catch (Exception e) {
            log.error("Failed to process customer for userId={}: {}", userId, e.getMessage(), e);
            processedUserIds.remove(userId); // Allow retry
        }
    }

}

