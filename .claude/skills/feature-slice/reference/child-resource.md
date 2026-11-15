# Variant: entity nested under a parent resource

Use when the entity belongs to another one and its URL is nested, e.g.
`/api/vehicles/{vehicleId}/documents`. Everything from backend.md / frontend.md applies, with
the differences below. The parent module stays untouched except for embedding the UI section in its
detail page (and a field on the parent, if the description requires one).

## Backend

- Own package (`document`), not inside the parent package.
- Reference the parent by id, not by a JPA relation (keeps packages decoupled, no lazy loading):

  ```java
  @Column(name = "vehicle_id", nullable = false)
  private Long vehicleId;
  ```

  ```sql
  vehicle_id BIGINT NOT NULL,
  CONSTRAINT fk_document_vehicle FOREIGN KEY (vehicle_id) REFERENCES vehicle (id) ON DELETE CASCADE
  ```

  `ON DELETE CASCADE` so deleting the parent keeps working. Add an index-friendly constraint
  name `fk_{table}_{parent}`.
- The parent id comes from the path, never from the request body. Request record has no parent id;
  the constructor / `toEntity(parentId, request)` takes it. Response includes `{parent}Id`.
- Repository:

  ```java
  List<Document> findAllByVehicleIdOrderById(Long vehicleId);

  Optional<Document> findByIdAndVehicleId(Long id, Long vehicleId);
  ```

  plus `existsBy...AndVehicleId...` queries for per-parent uniqueness rules.
- Service injects the parent repository too:

  ```java
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
  ```

  Every method starts with `requireVehicle` (404 for a missing parent even when listing). A child
  that exists but belongs to another parent is a 404, not a 403.
- Controller: `@RequestMapping("/api/vehicles/{vehicleId}/documents")`, every method takes
  `@PathVariable Long vehicleId`. Location header via `fromCurrentRequest().path("/{id}")`, giving
  `http://localhost/api/vehicles/1/documents/5` in tests.
- Generate only the endpoints in the contract. Non-CRUD actions (e.g. `PUT .../{id}/status` with a
  one-field request record) get the same treatment: a request record with validation, a service
  method with `@Transactional`, controller + tests for 200/400/404/409. Business rules (allowed
  transitions, readiness calculations) are written by hand on top; the skill provides the skeleton,
  with a `// TODO business rule` only if the rule is not specified.
- Tests: controller tests use the nested URL and also assert the parent 404
  (`"Vehicle with id 99 not found"`); service tests mock both repositories and cover parent missing,
  child missing, child of another parent; repository test saves a real parent row first (or uses a seeded
  vehicle id) and checks the per-parent queries return only that parent's rows.

## Frontend

- No own routes and no nav link. The UI is a section component embedded in the parent detail page:
  `frontend/src/app/{fe-folder}/{fe-file}-section.ts/.html` with input `{parent}Id`, placed inside
  the parent's `<section>` (replace the placeholder section, e.g. `<h2>Documents</h2>`):
  `<app-document-section [vehicleId]="vehicle.id" />`.
- API service methods take the parent id:

  ```ts
  list(vehicleId: Signal<number | undefined>) {
    return httpResource<Document[]>(
      () => {
        const id = vehicleId();
        return id === undefined ? undefined : `/api/vehicles/${id}/documents`;
      },
      { defaultValue: [] },
    );
  }

  create(vehicleId: number, request: DocumentRequest): Observable<Document> { ... }
  ```

- The section shows a `<table>` of children (with `@empty` row) and an inline add `<form>` built the
  same way as the vehicle form (signal form, `required` only, `problemDetailToFormErrors`); after a
  successful save it resets the form model and calls `list.reload()` instead of navigating.
  Row actions (e.g. Verify / Reject buttons) call the API and reload, showing errors via
  `problemDetailMessage` in a `<p class="error" role="alert">` like the vehicle list delete.
- Spec `{fe-file}-section.spec.ts`: renders rows from GET, required errors, create sends the right body
  to the nested URL and reloads the list, 400 field errors, 409 message, row action error message.
