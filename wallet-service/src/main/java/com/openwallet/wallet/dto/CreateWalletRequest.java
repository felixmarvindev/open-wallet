package com.openwallet.wallet.dto;

import com.openwallet.wallet.validation.CurrencyCode;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
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
public class CreateWalletRequest {

    /**
     * Currency for the wallet. For MVP, only KES is supported.
     * If not provided, defaults to KES.
     * If provided, must be KES.
     */
    @CurrencyCode
    private String currency;

    @DecimalMin(value = "0.00", message = "Daily limit cannot be negative")
    private BigDecimal dailyLimit;

    @DecimalMin(value = "0.00", message = "Monthly limit cannot be negative")
    private BigDecimal monthlyLimit;
}

