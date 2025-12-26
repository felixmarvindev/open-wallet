package com.openwallet.wallet.exception;

import java.math.BigDecimal;

/**
 * Thrown when a wallet operation would result in a negative balance.
 */
public class InsufficientBalanceException extends RuntimeException {
    private final Long walletId;
    private final BigDecimal currentBalance;
    private final BigDecimal requestedAmount;

    public InsufficientBalanceException(Long walletId, BigDecimal currentBalance, BigDecimal requestedAmount) {
        super(String.format("Insufficient balance for wallet %d. Current: %s, Requested: %s", 
                walletId, currentBalance, requestedAmount));
        this.walletId = walletId;
        this.currentBalance = currentBalance;
        this.requestedAmount = requestedAmount;
    }

    public Long getWalletId() {
        return walletId;
    }

    public BigDecimal getCurrentBalance() {
        return currentBalance;
    }

    public BigDecimal getRequestedAmount() {
        return requestedAmount;
    }
}

