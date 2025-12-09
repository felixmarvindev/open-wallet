package com.openwallet.notification.listener;

import com.openwallet.notification.domain.Notification;
import com.openwallet.notification.events.KycEvent;
import com.openwallet.notification.events.TransactionEvent;
import com.openwallet.notification.repository.NotificationRepository;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.common.serialization.StringSerializer;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.kafka.core.DefaultKafkaProducerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.test.EmbeddedKafkaBroker;
import org.springframework.kafka.test.context.EmbeddedKafka;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;

import java.math.BigDecimal;
import java.time.Duration;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@EmbeddedKafka(partitions = 1, topics = {"transaction-events", "kyc-events"})
@ActiveProfiles("test")
@TestPropertySource(properties = {
        "spring.kafka.bootstrap-servers=${spring.embedded.kafka.brokers}",
        "spring.kafka.consumer.auto-offset-reset=earliest",
        "spring.kafka.group-id=notification-it"
})
@SuppressWarnings("null")
class NotificationIntegrationTest {

    @Autowired
    private EmbeddedKafkaBroker embeddedKafkaBroker;

    @Autowired
    private NotificationRepository notificationRepository;

    private KafkaTemplate<String, TransactionEvent> transactionTemplate;
    private KafkaTemplate<String, KycEvent> kycTemplate;

    @BeforeEach
    void setup() {
        notificationRepository.deleteAll();
        transactionTemplate = new KafkaTemplate<>(new DefaultKafkaProducerFactory<>(producerProps()));
        kycTemplate = new KafkaTemplate<>(new DefaultKafkaProducerFactory<>(producerProps()));
    }

    @Test
    void transactionCompletedEventCreatesNotification() throws Exception {
        TransactionEvent event = TransactionEvent.builder()
                .transactionId(100L)
                .eventType("TRANSACTION_COMPLETED")
                .amount(new BigDecimal("50.00"))
                .currency("KES")
                .toWalletId(200L)
                .build();

        transactionTemplate.send("transaction-events", event).get();

        List<Notification> notifications = awaitNotifications();
        assertThat(notifications).hasSize(1);
        assertThat(notifications.get(0).getNotificationType()).isEqualTo("TRANSACTION_COMPLETED");
    }

    @Test
    void kycVerifiedEventCreatesNotification() throws Exception {
        KycEvent event = KycEvent.builder()
                .kycCheckId(300L)
                .customerId(400L)
                .userId("user-kyc-it")
                .eventType("KYC_VERIFIED")
                .build();

        kycTemplate.send("kyc-events", event).get();

        List<Notification> notifications = awaitNotifications();
        assertThat(notifications).hasSize(1);
        assertThat(notifications.get(0).getNotificationType()).isEqualTo("KYC_VERIFIED");
    }

    private Map<String, Object> producerProps() {
        Map<String, Object> props = new HashMap<>();
        props.put(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, embeddedKafkaBroker.getBrokersAsString());
        props.put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer.class);
        props.put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, org.springframework.kafka.support.serializer.JsonSerializer.class);
        return props;
    }

    private List<Notification> awaitNotifications() throws InterruptedException {
        int attempts = 10;
        while (attempts-- > 0) {
            List<Notification> all = notificationRepository.findAll();
            if (!all.isEmpty()) {
                return all;
            }
            Thread.sleep(Duration.ofMillis(200).toMillis());
        }
        return notificationRepository.findAll();
    }
}


