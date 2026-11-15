package com.example.dealership.document;

import java.net.URI;
import java.util.List;

import jakarta.validation.Valid;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

@RestController
@RequestMapping("/api/vehicles/{vehicleId}/documents")
public class DocumentController {

    private final DocumentService documentService;

    public DocumentController(DocumentService documentService) {
        this.documentService = documentService;
    }

    @GetMapping
    public List<DocumentResponse> findAll(@PathVariable Long vehicleId) {
        return documentService.findAll(vehicleId);
    }

    @PostMapping
    public ResponseEntity<DocumentResponse> create(@PathVariable Long vehicleId,
            @Valid @RequestBody DocumentRequest request) {
        DocumentResponse response = documentService.create(vehicleId, request);
        URI location = ServletUriComponentsBuilder.fromCurrentRequest()
                .path("/{id}")
                .buildAndExpand(response.id())
                .toUri();
        return ResponseEntity.created(location).body(response);
    }

    @PutMapping("/{id}/status")
    public DocumentResponse changeStatus(@PathVariable Long vehicleId, @PathVariable Long id,
            @Valid @RequestBody DocumentStatusRequest request) {
        return documentService.changeStatus(vehicleId, id, request);
    }
}
