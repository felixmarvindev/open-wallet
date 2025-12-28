package com.openwallet.ledger.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

/**
 * DTO for wallet limits retrieved from wallets table (read-only access).
 * Used for transaction limit validation without creating a JPA entity.
 */
@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class WalletLimits {
    private Long walletId;
    private BigDecimal dailyLimit;
    private BigDecimal monthlyLimit;
}

