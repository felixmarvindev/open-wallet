package com.openwallet.wallet.validation;

import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

/**
 * Validates ISO 4217 currency codes.
 * Supports common currencies: KES, USD, EUR, GBP, JPY, CNY, etc.
 */
public class CurrencyCodeValidator implements ConstraintValidator<CurrencyCode, String> {

    private static final Set<String> VALID_CURRENCIES = new HashSet<>(Arrays.asList(
            "KES", "USD", "EUR", "GBP", "JPY", "CNY", "INR", "AUD", "CAD", "CHF",
            "NZD", "SGD", "HKD", "SEK", "NOK", "DKK", "ZAR", "BRL", "MXN", "ARS"
    ));

    @Override
    public void initialize(CurrencyCode constraintAnnotation) {
        // No initialization needed
    }

    @Override
    public boolean isValid(String value, ConstraintValidatorContext context) {
        if (value == null) {
            return true; // Let @NotNull handle null validation
        }
        return VALID_CURRENCIES.contains(value.toUpperCase());
    }
}

