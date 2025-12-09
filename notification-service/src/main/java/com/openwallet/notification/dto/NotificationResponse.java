package com.openwallet.notification.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class NotificationResponse {
    private Long id;
    private String recipient;
    private String notificationType;
    private String channel;
    private String content;
    private String status;
    private String sentAt;
    private String createdAt;
}

