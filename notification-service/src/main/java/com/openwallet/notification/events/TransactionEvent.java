package com.openwallet.notification.events;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class TransactionEvent {
    private Long transactionId;
    private String eventType; // TRANSACTION_INITIATED, TRANSACTION_COMPLETED, TRANSACTION_FAILED
    private String transactionType;
    private String status;
    private BigDecimal amount;
    private String currency;
    private Long fromWalletId;
    private Long toWalletId;
    private LocalDateTime completedAt;
    private String failureReason;
}


