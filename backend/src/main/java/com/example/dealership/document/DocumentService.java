package com.example.dealership.document;

import java.util.List;

import com.example.dealership.error.ConflictException;
import com.example.dealership.error.NotFoundException;
import com.example.dealership.vehicle.VehicleRepository;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
public class DocumentService {

    private final DocumentRepository documentRepository;
    private final VehicleRepository vehicleRepository;

    public DocumentService(DocumentRepository documentRepository, VehicleRepository vehicleRepository) {
        this.documentRepository = documentRepository;
        this.vehicleRepository = vehicleRepository;
    }

    public List<DocumentResponse> findAll(Long vehicleId) {
        requireVehicle(vehicleId);
        return documentRepository.findAllByVehicleIdOrderById(vehicleId).stream()
                .map(DocumentMapper::toResponse)
                .toList();
    }

    // One active (not rejected) document per type and vehicle (INTENT A6).
    @Transactional
    public DocumentResponse create(Long vehicleId, DocumentRequest request) {
        requireVehicle(vehicleId);
        if (documentRepository.existsByVehicleIdAndTypeAndStatusNot(
                vehicleId, request.type(), DocumentStatus.REJECTED)) {
            throw new ConflictException(
                    "Vehicle with id " + vehicleId + " already has an active " + request.type() + " document");
        }
        Document document = documentRepository.save(DocumentMapper.toEntity(vehicleId, request));
        return DocumentMapper.toResponse(document);
    }

    @Transactional
    public DocumentResponse changeStatus(Long vehicleId, Long id, DocumentStatusRequest request) {
        Document document = getDocument(vehicleId, id);
        // TODO business rule: allowed status transitions (INTENT A7, A8, Z3) are written by hand.
        document.setStatus(request.status());
        return DocumentMapper.toResponse(documentRepository.save(document));
    }

    private void requireVehicle(Long vehicleId) {
        if (!vehicleRepository.existsById(vehicleId)) {
            throw new NotFoundException("Vehicle with id " + vehicleId + " not found");
        }
    }

    private Document getDocument(Long vehicleId, Long id) {
        requireVehicle(vehicleId);
        return documentRepository.findByIdAndVehicleId(id, vehicleId)
                .orElseThrow(() -> new NotFoundException("Document with id " + id + " not found"));
    }
}
