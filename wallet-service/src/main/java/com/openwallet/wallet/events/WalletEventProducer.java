package com.openwallet.wallet.events;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

/**
 * Kafka producer for wallet lifecycle events.
 */
@Component
@RequiredArgsConstructor
@Slf4j
@SuppressWarnings("null")
public class WalletEventProducer {

    private final KafkaTemplate<String, WalletEvent> walletEventKafkaTemplate;

    @Value("${app.topics.wallet-events:wallet-events}")
    private String topic;

    /**
     * Publish a wallet event to Kafka.
     *
     * @param event Wallet event to publish
     */
    public void publish(WalletEvent event) {
        String key = event.getWalletId() != null ? event.getWalletId().toString() : event.getUserId();
        
        walletEventKafkaTemplate.send(topic, key, event)
                .whenComplete((result, ex) -> {
                    if (ex != null) {
                        log.error("Failed to publish wallet event type={} walletId={}", 
                                event.getEventType(), event.getWalletId(), ex);
                    } else {
                        log.info("Published wallet event type={} walletId={} customerId={}", 
                                event.getEventType(), event.getWalletId(), event.getCustomerId());
                    }
                });
    }
}

