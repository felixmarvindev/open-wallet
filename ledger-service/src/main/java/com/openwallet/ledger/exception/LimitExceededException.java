package com.openwallet.ledger.exception;

import java.math.BigDecimal;

/**
 * Exception thrown when a transaction would exceed wallet transaction limits.
 */
public class LimitExceededException extends RuntimeException {
    
    private final Long walletId;
    private final String limitType; // "DAILY" or "MONTHLY"
    private final BigDecimal currentUsage;
    private final BigDecimal limit;
    private final BigDecimal requestedAmount;

    public LimitExceededException(Long walletId, String limitType, 
                                  BigDecimal currentUsage, BigDecimal limit, BigDecimal requestedAmount) {
        super(String.format("Transaction limit exceeded for walletId=%d: %s limit is %.2f, current usage is %.2f, requested amount is %.2f",
                walletId, limitType, limit, currentUsage, requestedAmount));
        this.walletId = walletId;
        this.limitType = limitType;
        this.currentUsage = currentUsage;
        this.limit = limit;
        this.requestedAmount = requestedAmount;
    }

    public Long getWalletId() {
        return walletId;
    }

    public String getLimitType() {
        return limitType;
    }

    public BigDecimal getCurrentUsage() {
        return currentUsage;
    }

    public BigDecimal getLimit() {
        return limit;
    }

    public BigDecimal getRequestedAmount() {
        return requestedAmount;
    }
}

