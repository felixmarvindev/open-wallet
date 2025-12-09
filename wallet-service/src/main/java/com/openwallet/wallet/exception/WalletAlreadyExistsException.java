package com.openwallet.wallet.exception;

/**
 * Thrown when a wallet with the same customer and currency already exists.
 */
public class WalletAlreadyExistsException extends RuntimeException {
    public WalletAlreadyExistsException(String message) {
        super(message);
    }
}


