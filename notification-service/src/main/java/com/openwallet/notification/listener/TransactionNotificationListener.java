package com.openwallet.notification.listener;

import com.openwallet.notification.domain.NotificationChannel;
import com.openwallet.notification.events.TransactionEvent;
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
public class TransactionNotificationListener {

    private static final Logger log = LoggerFactory.getLogger(TransactionNotificationListener.class);

    private final NotificationSender notificationSender;

    private final Set<String> processed = ConcurrentHashMap.newKeySet();

    @KafkaListener(
            topics = "${app.topics.transaction-events:transaction-events}",
            groupId = "${spring.kafka.group-id:notification-service}",
            containerFactory = "transactionEventKafkaListenerContainerFactory")
    public void handle(TransactionEvent event) {
        if (event == null || event.getEventType() == null) {
            return;
        }
        String key = "tx-" + event.getEventType() + "-" + event.getTransactionId();
        if (!processed.add(key)) {
            log.debug("Skipping duplicate transaction event {}", key);
            return;
        }

        if ("TRANSACTION_COMPLETED".equals(event.getEventType())) {
            String content = String.format("Transaction %s completed amount %s %s", event.getTransactionId(), event.getAmount(), event.getCurrency());
            notificationSender.send("TRANSACTION_COMPLETED", recipientFrom(event), NotificationChannel.SMS, content);
        } else if ("TRANSACTION_FAILED".equals(event.getEventType())) {
            String content = String.format("Transaction %s failed: %s", event.getTransactionId(), event.getFailureReason());
            notificationSender.send("TRANSACTION_FAILED", recipientFrom(event), NotificationChannel.SMS, content);
        }

        log.info("Consumed transaction event type={} txId={}", event.getEventType(), event.getTransactionId());
    }

    private String recipientFrom(TransactionEvent event) {
        // Placeholder: route to phone/email based on wallet owner if available. For now, use wallet ID as surrogate.
        if (event.getToWalletId() != null) {
            return "wallet-" + event.getToWalletId();
        }
        if (event.getFromWalletId() != null) {
            return "wallet-" + event.getFromWalletId();
        }
        return "transaction-" + event.getTransactionId();
    }
}


