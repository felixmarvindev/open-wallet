package com.openwallet.wallet.exception;

/**
 * Thrown when a wallet is not found or not accessible for the requesting customer.
 */
public class WalletNotFoundException extends RuntimeException {
    public WalletNotFoundException(String message) {
        super(message);
    }
}


