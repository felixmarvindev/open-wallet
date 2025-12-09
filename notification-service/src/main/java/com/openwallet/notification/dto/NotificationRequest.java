package com.openwallet.notification.dto;

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

    @NotBlank
    private String recipient;

    @NotBlank
    private String notificationType;

    @NotNull
    private String channel; // SMS or EMAIL

    @NotBlank
    private String content;
}

