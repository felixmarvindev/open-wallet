package com.openwallet.wallet.events;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * Event representing wallet lifecycle events.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class WalletEvent {
    private Long walletId;
    private Long customerId;
    private String userId;
    private String eventType;
    private BigDecimal balance;
    private String currency;
    private LocalDateTime timestamp;
}

