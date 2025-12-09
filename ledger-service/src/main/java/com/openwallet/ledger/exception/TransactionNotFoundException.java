package com.openwallet.ledger.exception;

/**
 * Thrown when a transaction cannot be found.
 */
public class TransactionNotFoundException extends RuntimeException {
    public TransactionNotFoundException(String message) {
        super(message);
    }
}
