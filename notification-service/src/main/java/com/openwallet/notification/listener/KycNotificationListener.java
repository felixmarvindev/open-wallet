package com.openwallet.notification.listener;

import com.openwallet.notification.domain.NotificationChannel;
import com.openwallet.notification.events.KycEvent;
import com.openwallet.notification.service.NotificationSender;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

@Component
@RequiredArgsConstructor
@SuppressWarnings("null")
public class KycNotificationListener {

    private static final Logger log = LoggerFactory.getLogger(KycNotificationListener.class);

    private final NotificationSender notificationSender;

    private final Set<String> processed = ConcurrentHashMap.newKeySet();

    @KafkaListener(
            topics = "${app.topics.kyc-events:kyc-events}",
            groupId = "${spring.kafka.group-id:notification-service}",
            containerFactory = "kycEventKafkaListenerContainerFactory")
    public void handle(KycEvent event) {
        if (event == null || event.getEventType() == null) {
            return;
        }
        String key = "kyc-" + event.getEventType() + "-" + event.getKycCheckId();
        if (!processed.add(key)) {
            log.debug("Skipping duplicate KYC event {}", key);
            return;
        }

        if ("KYC_VERIFIED".equals(event.getEventType())) {
            String content = String.format("KYC verified for user %s", event.getUserId());
            notificationSender.send("KYC_VERIFIED", recipientFrom(event), NotificationChannel.EMAIL, content);
        } else if ("KYC_REJECTED".equals(event.getEventType())) {
            String content = String.format("KYC rejected for user %s: %s", event.getUserId(), event.getRejectionReason());
            notificationSender.send("KYC_REJECTED", recipientFrom(event), NotificationChannel.EMAIL, content);
        }

        log.info("Consumed KYC event type={} kycId={}", event.getEventType(), event.getKycCheckId());
    }

    private String recipientFrom(KycEvent event) {
        return event.getUserId() != null ? event.getUserId() : "customer-" + event.getCustomerId();
    }
}


