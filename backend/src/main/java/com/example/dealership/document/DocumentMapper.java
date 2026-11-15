package com.example.dealership.document;

public final class DocumentMapper {

    private DocumentMapper() {
    }

    // A new document always starts as PENDING (INTENT A3): the status is not part of the request.
    public static Document toEntity(Long vehicleId, DocumentRequest request) {
        return new Document(
                vehicleId,
                request.type(),
                request.number(),
                request.issueDate(),
                request.expiryDate(),
                DocumentStatus.PENDING);
    }

    public static DocumentResponse toResponse(Document document) {
        return new DocumentResponse(
                document.getId(),
                document.getVehicleId(),
                document.getType(),
                document.getNumber(),
                document.getIssueDate(),
                document.getExpiryDate(),
                document.getStatus());
    }
}
