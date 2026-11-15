package com.example.dealership.document;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import com.example.dealership.error.ConflictException;
import com.example.dealership.error.NotFoundException;
import com.example.dealership.vehicle.VehicleRepository;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class DocumentServiceTest {

    private static final String NUMBER = "FV/2026/10/118";
    private static final String OTHER_NUMBER = "OC-7781-2026";

    @Mock
    private DocumentRepository documentRepository;

    @Mock
    private VehicleRepository vehicleRepository;

    @InjectMocks
    private DocumentService documentService;

    @Test
    void findAllReturnsMappedDocumentsOfVehicle() {
        when(vehicleRepository.existsById(1L)).thenReturn(true);
        when(documentRepository.findAllByVehicleIdOrderById(1L))
                .thenReturn(List.of(document(NUMBER, DocumentStatus.PENDING), document(OTHER_NUMBER, DocumentStatus.REJECTED)));

        List<DocumentResponse> responses = documentService.findAll(1L);

        assertThat(responses).extracting(DocumentResponse::number).containsExactly(NUMBER, OTHER_NUMBER);
    }

    @Test
    void findAllThrowsNotFoundWhenVehicleDoesNotExist() {
        when(vehicleRepository.existsById(99L)).thenReturn(false);

        assertThatThrownBy(() -> documentService.findAll(99L))
                .isInstanceOf(NotFoundException.class)
                .hasMessage("Vehicle with id 99 not found");
    }

    @Test
    void createSavesPendingDocumentForVehicle() {
        when(vehicleRepository.existsById(1L)).thenReturn(true);
        when(documentRepository.existsByVehicleIdAndTypeAndStatusNot(1L, DocumentType.OC_POLICY, DocumentStatus.REJECTED))
                .thenReturn(false);
        when(documentRepository.save(any(Document.class))).thenAnswer(invocation -> invocation.getArgument(0));

        DocumentResponse response = documentService.create(1L, request());

        assertThat(response.vehicleId()).isEqualTo(1L);
        assertThat(response.type()).isEqualTo(DocumentType.OC_POLICY);
        assertThat(response.number()).isEqualTo(NUMBER);
        assertThat(response.issueDate()).isEqualTo(LocalDate.of(2026, 10, 20));
        assertThat(response.expiryDate()).isEqualTo(LocalDate.of(2027, 10, 19));
        assertThat(response.status()).isEqualTo(DocumentStatus.PENDING);
    }

    @Test
    void createThrowsConflictWhenActiveDocumentOfTypeAlreadyExists() {
        when(vehicleRepository.existsById(1L)).thenReturn(true);
        when(documentRepository.existsByVehicleIdAndTypeAndStatusNot(1L, DocumentType.OC_POLICY, DocumentStatus.REJECTED))
                .thenReturn(true);

        assertThatThrownBy(() -> documentService.create(1L, request()))
                .isInstanceOf(ConflictException.class)
                .hasMessage("Vehicle with id 1 already has an active OC_POLICY document");
        verify(documentRepository, never()).save(any());
    }

    @Test
    void createThrowsNotFoundWhenVehicleDoesNotExist() {
        when(vehicleRepository.existsById(99L)).thenReturn(false);

        assertThatThrownBy(() -> documentService.create(99L, request()))
                .isInstanceOf(NotFoundException.class)
                .hasMessage("Vehicle with id 99 not found");
        verify(documentRepository, never()).save(any());
    }

    @Test
    void changeStatusUpdatesExistingDocument() {
        Document document = document(NUMBER, DocumentStatus.PENDING);
        when(vehicleRepository.existsById(1L)).thenReturn(true);
        when(documentRepository.findByIdAndVehicleId(5L, 1L)).thenReturn(Optional.of(document));
        when(documentRepository.save(document)).thenReturn(document);

        DocumentResponse response = documentService.changeStatus(1L, 5L, new DocumentStatusRequest(DocumentStatus.VERIFIED));

        assertThat(response.status()).isEqualTo(DocumentStatus.VERIFIED);
        assertThat(document.getStatus()).isEqualTo(DocumentStatus.VERIFIED);
        verify(documentRepository).save(document);
    }

    @ParameterizedTest(name = "{0} -> {1}")
    @CsvSource({ "PENDING, VERIFIED", "PENDING, REJECTED" })
    void changeStatusAllowsDecidingPendingDocument(DocumentStatus from, DocumentStatus to) {
        Document document = document(NUMBER, from);
        when(vehicleRepository.existsById(1L)).thenReturn(true);
        when(documentRepository.findByIdAndVehicleId(5L, 1L)).thenReturn(Optional.of(document));
        when(documentRepository.save(document)).thenReturn(document);

        DocumentResponse response = documentService.changeStatus(1L, 5L, new DocumentStatusRequest(to));

        assertThat(response.status()).isEqualTo(to);
    }

    @ParameterizedTest(name = "{0} -> {1}")
    @CsvSource({
            "PENDING, PENDING",
            "VERIFIED, PENDING",
            "VERIFIED, VERIFIED",
            "VERIFIED, REJECTED",
            "REJECTED, PENDING",
            "REJECTED, VERIFIED",
            "REJECTED, REJECTED" })
    void changeStatusThrowsConflictWhenTransitionIsNotAllowed(DocumentStatus from, DocumentStatus to) {
        Document document = document(NUMBER, from);
        when(vehicleRepository.existsById(1L)).thenReturn(true);
        when(documentRepository.findByIdAndVehicleId(5L, 1L)).thenReturn(Optional.of(document));

        assertThatThrownBy(() -> documentService.changeStatus(1L, 5L, new DocumentStatusRequest(to)))
                .isInstanceOf(ConflictException.class)
                .hasMessage("Document with id 5 cannot change status from " + from + " to " + to);
        assertThat(document.getStatus()).isEqualTo(from);
        verify(documentRepository, never()).save(any());
    }

    @Test
    void changeStatusThrowsConflictWhenRejectedDocumentIsVerified() {
        // INTENT A7: a correction from the customer is a new document, not a re-verification.
        Document document = document(NUMBER, DocumentStatus.REJECTED);
        when(vehicleRepository.existsById(1L)).thenReturn(true);
        when(documentRepository.findByIdAndVehicleId(5L, 1L)).thenReturn(Optional.of(document));

        assertThatThrownBy(() -> documentService.changeStatus(1L, 5L, new DocumentStatusRequest(DocumentStatus.VERIFIED)))
                .isInstanceOf(ConflictException.class)
                .hasMessage("Document with id 5 cannot change status from REJECTED to VERIFIED");
    }

    @Test
    void changeStatusThrowsConflictWhenVerifiedDocumentIsRejected() {
        // INTENT Z3 / client question 2: a verified document is final for now.
        Document document = document(NUMBER, DocumentStatus.VERIFIED);
        when(vehicleRepository.existsById(1L)).thenReturn(true);
        when(documentRepository.findByIdAndVehicleId(5L, 1L)).thenReturn(Optional.of(document));

        assertThatThrownBy(() -> documentService.changeStatus(1L, 5L, new DocumentStatusRequest(DocumentStatus.REJECTED)))
                .isInstanceOf(ConflictException.class)
                .hasMessage("Document with id 5 cannot change status from VERIFIED to REJECTED");
    }

    @Test
    void changeStatusThrowsConflictWhenStatusIsUnchanged() {
        Document document = document(NUMBER, DocumentStatus.PENDING);
        when(vehicleRepository.existsById(1L)).thenReturn(true);
        when(documentRepository.findByIdAndVehicleId(5L, 1L)).thenReturn(Optional.of(document));

        assertThatThrownBy(() -> documentService.changeStatus(1L, 5L, new DocumentStatusRequest(DocumentStatus.PENDING)))
                .isInstanceOf(ConflictException.class)
                .hasMessage("Document with id 5 cannot change status from PENDING to PENDING");
    }

    @Test
    void changeStatusThrowsNotFoundWhenVehicleDoesNotExist() {
        when(vehicleRepository.existsById(99L)).thenReturn(false);

        assertThatThrownBy(() -> documentService.changeStatus(99L, 5L, new DocumentStatusRequest(DocumentStatus.VERIFIED)))
                .isInstanceOf(NotFoundException.class)
                .hasMessage("Vehicle with id 99 not found");
    }

    @Test
    void changeStatusThrowsNotFoundWhenDocumentDoesNotExistOrBelongsToAnotherVehicle() {
        when(vehicleRepository.existsById(1L)).thenReturn(true);
        when(documentRepository.findByIdAndVehicleId(99L, 1L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> documentService.changeStatus(1L, 99L, new DocumentStatusRequest(DocumentStatus.VERIFIED)))
                .isInstanceOf(NotFoundException.class)
                .hasMessage("Document with id 99 not found");
        verify(documentRepository, never()).save(any());
    }

    private static Document document(String number, DocumentStatus status) {
        return new Document(1L, DocumentType.OC_POLICY, number,
                LocalDate.of(2026, 10, 20), LocalDate.of(2027, 10, 19), status);
    }

    private static DocumentRequest request() {
        return new DocumentRequest(DocumentType.OC_POLICY, NUMBER, LocalDate.of(2026, 10, 20), LocalDate.of(2027, 10, 19));
    }
}
