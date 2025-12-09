package com.openwallet.wallet.dto;

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
public class WalletResponse {
    private Long id;
    private Long customerId;
    private String currency;
    private String status;
    private BigDecimal balance;
    private BigDecimal dailyLimit;
    private BigDecimal monthlyLimit;
}

