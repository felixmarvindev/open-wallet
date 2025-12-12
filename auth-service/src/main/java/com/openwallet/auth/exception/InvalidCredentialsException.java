package com.openwallet.auth.exception;

/**
 * Exception thrown when authentication fails due to invalid credentials.
 */
public class InvalidCredentialsException extends RuntimeException {
    public InvalidCredentialsException(String message) {
        super(message);
    }
}

