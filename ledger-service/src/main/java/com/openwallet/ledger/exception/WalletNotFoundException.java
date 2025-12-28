package com.openwallet.ledger.exception;

/**
 * Thrown when a wallet cannot be found during limit validation.
 */
public class WalletNotFoundException extends RuntimeException {
    public WalletNotFoundException(String message) {
        super(message);
    }
}

