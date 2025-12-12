package com.openwallet.notification.validation;

import jakarta.validation.Constraint;
import jakarta.validation.Payload;
import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Validates that a notification channel is valid.
 */
@Documented
@Constraint(validatedBy = NotificationChannelValidator.class)
@Target({ElementType.FIELD, ElementType.PARAMETER})
@Retention(RetentionPolicy.RUNTIME)
public @interface NotificationChannel {
    String message() default "Invalid notification channel. Must be SMS or EMAIL";
    Class<?>[] groups() default {};
    Class<? extends Payload>[] payload() default {};
}


