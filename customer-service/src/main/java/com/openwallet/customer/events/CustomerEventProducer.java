package com.openwallet.customer.events;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

/**
 * Kafka producer for customer lifecycle events.
 */
@Component
@RequiredArgsConstructor
@Slf4j
@SuppressWarnings("null")
public class CustomerEventProducer {

    private final KafkaTemplate<String, CustomerEvent> customerEventKafkaTemplate;

    @Value("${app.topics.customer-events:customer-events}")
    private String topic;

    /**
     * Publish a customer event to Kafka.
     *
     * @param event Customer event to publish
     */
    public void publish(CustomerEvent event) {
        String key = event.getCustomerId() != null ? event.getCustomerId().toString() : event.getUserId();
        
        customerEventKafkaTemplate.send(topic, key, event)
                .whenComplete((result, ex) -> {
                    if (ex != null) {
                        log.error("Failed to publish customer event type={} customerId={}", 
                                event.getEventType(), event.getCustomerId(), ex);
                    } else {
                        log.info("Published customer event type={} customerId={} userId={}", 
                                event.getEventType(), event.getCustomerId(), event.getUserId());
                    }
                });
    }
}

