package com.openwallet.customer.events;

import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
@SuppressWarnings("null")
public class KycEventProducer {

    private static final Logger log = LoggerFactory.getLogger(KycEventProducer.class);

    private final KafkaTemplate<String, KycEvent> kafkaTemplate;

    @Value("${app.topics.kyc-events:kyc-events}")
    private String kycEventsTopic;

    public void publish(KycEvent event) {
        kafkaTemplate.send(kycEventsTopic,
                event.getCustomerId() != null ? event.getCustomerId().toString() : null,
                event);
        log.info("Published KYC event type={} customerId={} kycId={}",
                event.getEventType(), event.getCustomerId(), event.getKycCheckId());
    }
}


