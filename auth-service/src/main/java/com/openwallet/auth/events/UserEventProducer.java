package com.openwallet.auth.events;

import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

/**
 * Producer for publishing user lifecycle events to Kafka.
 */
@Component
@RequiredArgsConstructor
@SuppressWarnings("null")
public class UserEventProducer {

    private static final Logger log = LoggerFactory.getLogger(UserEventProducer.class);

    private final KafkaTemplate<String, UserEvent> kafkaTemplate;

    @Value("${app.topics.user-events:user-events}")
    private String userEventsTopic;

    /**
     * Publishes a user lifecycle event to Kafka.
     *
     * @param event User event to publish
     */
    public void publish(UserEvent event) {
        kafkaTemplate.send(userEventsTopic,
                event.getUserId() != null ? event.getUserId() : null,
                event);
        log.info("Published user event type={} userId={} username={}",
                event.getEventType(), event.getUserId(), event.getUsername());
    }
}

