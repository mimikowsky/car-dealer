package com.example.dealership.document;

import java.util.EnumMap;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import com.example.dealership.error.ConflictException;
import com.example.dealership.error.NotFoundException;
import com.example.dealership.vehicle.VehicleRepository;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
public class DocumentService {

    // Only a pending document can be decided (INTENT A7, A8, Z3). Everything else, including
    // setting the same status again, is a 409. A rejected document stays rejected: a correction
    // is a new document.
    private static final Map<DocumentStatus, Set<DocumentStatus>> ALLOWED_TRANSITIONS = new EnumMap<>(Map.of(
            DocumentStatus.PENDING, EnumSet.of(DocumentStatus.VERIFIED, DocumentStatus.REJECTED)));

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
        DocumentStatus current = document.getStatus();
        DocumentStatus target = request.status();
        if (!ALLOWED_TRANSITIONS.getOrDefault(current, Set.of()).contains(target)) {
            throw new ConflictException(
                    "Document with id " + id + " cannot change status from " + current + " to " + target);
        }
        document.setStatus(target);
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
