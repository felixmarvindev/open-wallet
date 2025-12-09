package com.openwallet.ledger.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class TransactionResponse {
    private Long id;
    private String transactionType;
    private String status;
    private BigDecimal amount;
    private String currency;
    private Long fromWalletId;
    private Long toWalletId;
    private String initiatedAt;
    private String completedAt;
    private String failureReason;
}

