package com.openwallet.notification.validation;

import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;
import java.util.Set;

/**
 * Validates notification channel values.
 */
public class NotificationChannelValidator implements ConstraintValidator<NotificationChannel, String> {

    private static final Set<String> VALID_CHANNELS = Set.of("SMS", "EMAIL");

    @Override
    public void initialize(NotificationChannel constraintAnnotation) {
        // No initialization needed
    }

    @Override
    public boolean isValid(String value, ConstraintValidatorContext context) {
        if (value == null) {
            return true; // Let @NotNull handle null validation
        }
        return VALID_CHANNELS.contains(value.toUpperCase());
    }
}


