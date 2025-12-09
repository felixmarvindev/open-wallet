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
public class LedgerEntryResponse {
    private Long id;
    private Long transactionId;
    private Long walletId;
    private String accountType;
    private String entryType;
    private BigDecimal amount;
    private BigDecimal balanceAfter;
    private String createdAt;
}

