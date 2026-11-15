package com.example.dealership.document;

import java.time.LocalDate;

public record DocumentResponse(
        Long id,
        Long vehicleId,
        DocumentType type,
        String number,
        LocalDate issueDate,
        LocalDate expiryDate,
        DocumentStatus status
) {
}
