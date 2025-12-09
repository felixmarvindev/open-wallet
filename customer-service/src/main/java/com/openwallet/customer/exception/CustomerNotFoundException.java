package com.openwallet.customer.exception;

/**
 * Thrown when a customer profile cannot be found for the requesting user.
 */
public class CustomerNotFoundException extends RuntimeException {
    public CustomerNotFoundException(String message) {
        super(message);
    }
}


