package com.example.dealership.document;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

public interface DocumentRepository extends JpaRepository<Document, Long> {

    List<Document> findAllByVehicleIdOrderById(Long vehicleId);

    Optional<Document> findByIdAndVehicleId(Long id, Long vehicleId);

    boolean existsByVehicleIdAndTypeAndStatusNot(Long vehicleId, DocumentType type, DocumentStatus status);
}
