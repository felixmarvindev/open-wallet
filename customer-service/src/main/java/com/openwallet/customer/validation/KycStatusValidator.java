package com.openwallet.customer.validation;

import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

/**
 * Validates KYC status values for webhook requests.
 */
public class KycStatusValidator implements ConstraintValidator<KycStatus, String> {

    private static final Set<String> VALID_STATUSES = new HashSet<>(Arrays.asList("VERIFIED", "REJECTED"));

    @Override
    public void initialize(KycStatus constraintAnnotation) {
        // No initialization needed
    }

    @Override
    public boolean isValid(String value, ConstraintValidatorContext context) {
        if (value == null) {
            return true; // Let @NotBlank handle null validation
        }
        return VALID_STATUSES.contains(value.toUpperCase());
    }
}

