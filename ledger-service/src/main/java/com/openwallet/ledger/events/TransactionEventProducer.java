package com.openwallet.ledger.events;

import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
@SuppressWarnings("null")
public class TransactionEventProducer {

    private static final Logger log = LoggerFactory.getLogger(TransactionEventProducer.class);

    private final KafkaTemplate<String, TransactionEvent> kafkaTemplate;

    @Value("${app.topics.transaction-events:transaction-events}")
    private String transactionEventsTopic;

    public void publish(TransactionEvent event) {
        kafkaTemplate.send(transactionEventsTopic, event.getTransactionId() != null ? event.getTransactionId().toString() : null, event);
        log.info("Published transaction event type={} txId={}", event.getEventType(), event.getTransactionId());
    }
}


