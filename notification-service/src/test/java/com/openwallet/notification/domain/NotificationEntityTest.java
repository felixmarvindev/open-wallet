package com.openwallet.notification.domain;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.autoconfigure.orm.jpa.TestEntityManager;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;
import com.openwallet.notification.config.JpaConfig;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Integration test for Notification entity to verify JPA mapping and
 * persistence.
 */
@DataJpaTest
@Import(JpaConfig.class)
@ActiveProfiles("test")
class NotificationEntityTest {

    @Autowired
    private TestEntityManager entityManager;

    @Test
    void shouldPersistNotificationWithAllFields() {
        // Given
        Notification notification = Notification.builder()
                .recipient("+254712345678")
                .notificationType("TRANSACTION_COMPLETED")
                .channel(NotificationChannel.SMS)
                .content("Your transaction of KES 100.00 has been completed")
                .status(NotificationStatus.PENDING)
                .build();

        // When
        Notification saved = entityManager.persistAndFlush(notification);

        // Then
        assertThat(saved.getId()).isNotNull();
        assertThat(saved.getRecipient()).isEqualTo("+254712345678");
        assertThat(saved.getNotificationType()).isEqualTo("TRANSACTION_COMPLETED");
        assertThat(saved.getChannel()).isEqualTo(NotificationChannel.SMS);
        assertThat(saved.getContent()).contains("transaction of KES 100.00");
        assertThat(saved.getStatus()).isEqualTo(NotificationStatus.PENDING);
        assertThat(saved.getCreatedAt()).isNotNull();
    }

    @Test
    void shouldSetDefaultStatusToPending() {
        // Given
        Notification notification = Notification.builder()
                .recipient("user@example.com")
                .notificationType("KYC_VERIFIED")
                .channel(NotificationChannel.EMAIL)
                .content("Your KYC verification has been completed")
                .build();

        // When
        Notification saved = entityManager.persistAndFlush(notification);

        // Then
        assertThat(saved.getStatus()).isEqualTo(NotificationStatus.PENDING);
    }
}
