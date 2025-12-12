package com.openwallet.customer.validation;

import jakarta.validation.Constraint;
import jakarta.validation.Payload;
import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Validates that a KYC status is a valid value.
 */
@Documented
@Constraint(validatedBy = KycStatusValidator.class)
@Target({ElementType.FIELD, ElementType.PARAMETER})
@Retention(RetentionPolicy.RUNTIME)
public @interface KycStatus {
    String message() default "Invalid KYC status. Must be one of: VERIFIED, REJECTED";
    Class<?>[] groups() default {};
    Class<? extends Payload>[] payload() default {};
}


