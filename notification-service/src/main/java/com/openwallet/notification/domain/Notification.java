package com.openwallet.notification.domain;

import jakarta.persistence.*;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.*;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.time.LocalDateTime;

/**
 * Notification entity representing notification history for audit and tracking.
 */
@Entity
@Table(name = "notifications", indexes = {
    @Index(name = "idx_notifications_recipient", columnList = "recipient"),
    @Index(name = "idx_notifications_status", columnList = "status"),
    @Index(name = "idx_notifications_created_at", columnList = "created_at DESC")
})
@EntityListeners(AuditingEntityListener.class)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Notification {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "recipient", nullable = false, length = 255)
    @NotBlank(message = "Recipient is required")
    private String recipient; // Phone or email

    @Column(name = "notification_type", nullable = false, length = 50)
    @NotBlank(message = "Notification type is required")
    private String notificationType; // TRANSACTION_COMPLETED, KYC_VERIFIED, etc.

    @Enumerated(EnumType.STRING)
    @Column(name = "channel", nullable = false, length = 20)
    @NotNull(message = "Channel is required")
    private NotificationChannel channel; // SMS or EMAIL

    @Column(name = "content", nullable = false, columnDefinition = "TEXT")
    @NotBlank(message = "Content is required")
    private String content;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    @NotNull(message = "Status is required")
    @Builder.Default
    private NotificationStatus status = NotificationStatus.PENDING;

    @Column(name = "sent_at")
    private LocalDateTime sentAt;

    @CreatedDate
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;
}

