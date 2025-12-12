package com.openwallet.auth.events;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;
import java.util.Map;

/**
 * Event DTO for user lifecycle events published to Kafka.
 * Used for audit trail and event-driven customer creation.
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class UserEvent {
    private String userId; // Keycloak user ID
    private String eventType; // USER_REGISTERED, USER_LOGIN, USER_LOGOUT
    private String username;
    private String email;
    private LocalDateTime timestamp;
    private Map<String, Object> metadata; // Additional event metadata
}

