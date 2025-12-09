package com.openwallet.notification.service;

import com.openwallet.notification.domain.NotificationChannel;

public interface NotificationSender {
    void send(String notificationType, String recipient, NotificationChannel channel, String content);
}


