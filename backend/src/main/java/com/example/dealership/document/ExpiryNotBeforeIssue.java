package com.example.dealership.document;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

import jakarta.validation.Constraint;
import jakarta.validation.Payload;

@Documented
@Constraint(validatedBy = ExpiryNotBeforeIssueValidator.class)
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
public @interface ExpiryNotBeforeIssue {

    String message() default "must not be before issue date";

    Class<?>[] groups() default {};

    Class<? extends Payload>[] payload() default {};
}
