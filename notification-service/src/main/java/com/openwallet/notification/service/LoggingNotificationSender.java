package com.openwallet.notification.service;

import com.openwallet.notification.domain.Notification;
import com.openwallet.notification.domain.NotificationChannel;
import com.openwallet.notification.domain.NotificationStatus;
import com.openwallet.notification.repository.NotificationRepository;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;

@Service
@RequiredArgsConstructor
@SuppressWarnings("null")
public class LoggingNotificationSender implements NotificationSender {

    private static final Logger log = LoggerFactory.getLogger(LoggingNotificationSender.class);

    private final NotificationRepository notificationRepository;

    @Override
    public void send(String notificationType, String recipient, NotificationChannel channel, String content) {
        String target = recipient != null ? recipient : "unknown-recipient";
        Notification notification = Notification.builder()
                .recipient(target)
                .notificationType(notificationType)
                .channel(channel)
                .content(content)
                .status(NotificationStatus.SENT)
                .sentAt(LocalDateTime.now())
                .build();
        notificationRepository.save(notification);
        log.info("Sent {} notification via {} to {}: {}", notificationType, channel, target, content);
    }
}


