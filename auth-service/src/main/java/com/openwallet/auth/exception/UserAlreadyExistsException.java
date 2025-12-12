package com.openwallet.auth.exception;

/**
 * Exception thrown when attempting to register a user that already exists.
 */
public class UserAlreadyExistsException extends RuntimeException {
    public UserAlreadyExistsException(String message) {
        super(message);
    }
}

