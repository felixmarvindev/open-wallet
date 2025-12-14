package com.openwallet.customer.events;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * Event representing customer lifecycle events.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CustomerEvent {
    private Long customerId;
    private String userId;
    private String email;
    private String eventType;
    private LocalDateTime timestamp;
}

