package com.example.dealership.document;

import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;

// Reported on expiryDate, not on the whole object, so the form shows it next to the field (INTENT Z5).
public class ExpiryNotBeforeIssueValidator implements ConstraintValidator<ExpiryNotBeforeIssue, DocumentRequest> {

    @Override
    public boolean isValid(DocumentRequest request, ConstraintValidatorContext context) {
        if (request == null || request.issueDate() == null || request.expiryDate() == null
                || !request.expiryDate().isBefore(request.issueDate())) {
            return true;
        }
        context.disableDefaultConstraintViolation();
        context.buildConstraintViolationWithTemplate(context.getDefaultConstraintMessageTemplate())
                .addPropertyNode("expiryDate")
                .addConstraintViolation();
        return false;
    }
}
