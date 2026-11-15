package com.example.dealership.document;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDate;

import com.example.dealership.vehicle.FuelType;
import com.example.dealership.vehicle.Vehicle;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.autoconfigure.orm.jpa.TestEntityManager;

@DataJpaTest
class DocumentRepositoryTest {

    @Autowired
    private DocumentRepository documentRepository;

    @Autowired
    private TestEntityManager entityManager;

    private Long vehicleId;
    private Long otherVehicleId;

    @BeforeEach
    void setUp() {
        vehicleId = entityManager.persistAndGetId(
                new Vehicle("WF0XXXGCDX0000004", "Ford", "Focus", 2026, FuelType.ELECTRIC, null), Long.class);
        otherVehicleId = entityManager.persistAndGetId(
                new Vehicle("WF0XXXGCDX0000005", "Ford", "Puma", 2025, FuelType.HYBRID, null), Long.class);
    }

    @Test
    void findAllByVehicleIdOrderByIdReturnsOnlyDocumentsOfThatVehicle() {
        Document first = documentRepository.save(document(vehicleId, DocumentType.INVOICE, DocumentStatus.PENDING));
        Document second = documentRepository.save(document(vehicleId, DocumentType.COC, DocumentStatus.REJECTED));
        documentRepository.save(document(otherVehicleId, DocumentType.INVOICE, DocumentStatus.PENDING));
        entityManager.flush();

        assertThat(documentRepository.findAllByVehicleIdOrderById(vehicleId))
                .extracting(Document::getId)
                .containsExactly(first.getId(), second.getId());
    }

    @Test
    void findByIdAndVehicleIdIgnoresDocumentOfAnotherVehicle() {
        Document document = documentRepository.save(document(vehicleId, DocumentType.INVOICE, DocumentStatus.PENDING));
        entityManager.flush();

        assertThat(documentRepository.findByIdAndVehicleId(document.getId(), vehicleId)).contains(document);
        assertThat(documentRepository.findByIdAndVehicleId(document.getId(), otherVehicleId)).isEmpty();
    }

    @Test
    void existsByVehicleIdAndTypeAndStatusNotFindsOnlyActiveDocumentsOfThatVehicle() {
        documentRepository.save(document(vehicleId, DocumentType.INVOICE, DocumentStatus.VERIFIED));
        documentRepository.save(document(vehicleId, DocumentType.COC, DocumentStatus.REJECTED));
        documentRepository.save(document(otherVehicleId, DocumentType.OC_POLICY, DocumentStatus.PENDING));
        entityManager.flush();

        assertThat(documentRepository.existsByVehicleIdAndTypeAndStatusNot(
                vehicleId, DocumentType.INVOICE, DocumentStatus.REJECTED)).isTrue();
        assertThat(documentRepository.existsByVehicleIdAndTypeAndStatusNot(
                vehicleId, DocumentType.COC, DocumentStatus.REJECTED)).isFalse();
        assertThat(documentRepository.existsByVehicleIdAndTypeAndStatusNot(
                vehicleId, DocumentType.OC_POLICY, DocumentStatus.REJECTED)).isFalse();
    }

    @Test
    void saveStoresDocumentThatCanBeReadBack() {
        Document saved = documentRepository.save(new Document(vehicleId, DocumentType.OC_POLICY, "OC-7781-2026",
                LocalDate.of(2026, 10, 20), LocalDate.of(2027, 10, 19), DocumentStatus.PENDING));
        entityManager.flush();
        entityManager.clear();

        Document found = documentRepository.findById(saved.getId()).orElseThrow();

        assertThat(found).isNotSameAs(saved);
        assertThat(found.getVehicleId()).isEqualTo(vehicleId);
        assertThat(found.getType()).isEqualTo(DocumentType.OC_POLICY);
        assertThat(found.getNumber()).isEqualTo("OC-7781-2026");
        assertThat(found.getIssueDate()).isEqualTo(LocalDate.of(2026, 10, 20));
        assertThat(found.getExpiryDate()).isEqualTo(LocalDate.of(2027, 10, 19));
        assertThat(found.getStatus()).isEqualTo(DocumentStatus.PENDING);
    }

    @Test
    void deletingVehicleDeletesItsDocuments() {
        Document document = documentRepository.save(document(vehicleId, DocumentType.INVOICE, DocumentStatus.PENDING));
        entityManager.flush();
        entityManager.clear();

        entityManager.remove(entityManager.find(Vehicle.class, vehicleId));
        entityManager.flush();
        entityManager.clear();

        assertThat(documentRepository.findById(document.getId())).isEmpty();
    }

    private static Document document(Long vehicleId, DocumentType type, DocumentStatus status) {
        return new Document(vehicleId, type, "NR-1", LocalDate.of(2026, 10, 1), null, status);
    }
}
