package com.openwallet.notification.repository;

import com.openwallet.notification.domain.Notification;
import com.openwallet.notification.domain.NotificationChannel;
import com.openwallet.notification.domain.NotificationStatus;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface NotificationRepository extends JpaRepository<Notification, Long> {
    List<Notification> findByRecipient(String recipient);

    List<Notification> findByStatus(NotificationStatus status);

    List<Notification> findByChannel(NotificationChannel channel);
}
