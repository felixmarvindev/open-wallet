package com.openwallet.notification.dto;

import com.openwallet.notification.validation.NotificationChannel;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
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
public class NotificationRequest {

    @NotBlank(message = "Recipient is required")
    private String recipient;

    @NotBlank(message = "Notification type is required")
    private String notificationType;

    @NotNull(message = "Channel is required")
    @NotificationChannel
    private String channel; // SMS or EMAIL

    @NotBlank(message = "Content is required")
    private String content;
}

