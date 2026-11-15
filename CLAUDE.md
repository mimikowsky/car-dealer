# CLAUDE.md

Dealership app for the fictional car dealer "Auto Mazur": Spring Boot backend + Angular frontend.
It replaces a paper checklist used to verify a car's documents before it is handed over to the
customer. The starting point is a recruitment task.

- `BRIEF.md`: the task (in Polish), the **fixed API contract** and the client meeting transcript.
  The contract (paths, enum names, 409 rules, `handover-readiness` shape) is tested externally:
  never rename or reshape it.
- `INTENT.md`: requirements, assumptions (Z1…), open client questions and out-of-scope items.
  Check it before implementing business rules; if code needs a new assumption, add it there.
- `DECISIONS.md`: what AI got wrong, what was done by hand and why (keep it updated).
- The vehicle module is **the reference pattern**. New features copy its structure, naming,
  error handling and tests. When unsure how to do something, read the vehicle equivalent first.

## Commands

The scripts are bash: on Windows run them with the Bash tool (Git Bash), not PowerShell.

```bash
./test.sh                          # all tests: backend (mvnw test) + frontend (vitest); must pass before commit
./run.sh                           # backend :8080 + frontend :4200 (proxies /api to :8080)
cd backend && ./mvnw test          # backend only
cd backend && ./mvnw test -Dtest=VehicleServiceTest   # single test class
cd frontend && npm test            # frontend only (ng test --watch=false, vitest + jsdom)
cd frontend && npm run build       # type-check templates/production build
cd frontend && npm run format      # prettier
```

Requirements: JDK 21, Node 22.22.3+/24.15+ (`.nvmrc`). Maven via wrapper only. H2 in memory,
recreated and seeded by Flyway on every start.

## Structure

```
backend/src/main/java/com/example/dealership/
  error/        GlobalExceptionHandler (ProblemDetail), NotFoundException, ConflictException, ValidationError
  vehicle/      reference feature: Entity, Request, Response, Mapper, Repository, Service, Controller,
                enum (FuelType), custom constraint (@ProductionYear + validator)
backend/src/main/resources/
  application.properties     ddl-auto=validate, fixed English locale, problemdetails enabled
  db/migration/V{n}__*.sql   Flyway
backend/src/test/java/...    mirrors main: <Name>ControllerTest, <Name>ServiceTest, <Name>RepositoryTest

frontend/src/app/
  app.routes.ts      lazy loadChildren per feature
  app.html           top nav: one routerLink per feature
  shared/            problem-detail.ts (backend error → message / form errors), route-params.ts
  vehicles/          vehicle-api.ts, vehicles.routes.ts, vehicle-list|detail|form .ts/.html, vehicle-form.spec.ts
frontend/src/styles.css      global class-free styles (no per-component CSS)
```

One package per feature on the backend (`customer/`, `document/`…), one folder per feature on the
frontend (`customers/`…), plural for frontend folders and URLs, singular for Java packages and tables.

## Backend conventions

- **Entity**: `@Entity @Table(name = "<singular_snake>")`, `IDENTITY` id, `@Column(nullable, length)`
  matching the migration, enums `@Enumerated(EnumType.STRING)`, protected no-arg constructor, public
  constructor with all fields except id, plain getters/setters (no Lombok).
- **Request / Response**: Java `record`s. Bean Validation lives on the Request
  (`@NotBlank`, `@Size(max)` equal to the column length, `@Pattern` with a human message, `@NotNull`
  for non-string required fields). Responses expose the id plus all fields.
- **Mapper**: `final` class with private constructor and static `toEntity`, `updateEntity`, `toResponse`.
- **Repository**: `JpaRepository<Entity, Long>`; uniqueness via `existsByX` and `existsByXAndIdNot`.
- **Service**: class-level `@Transactional(readOnly = true)`, `@Transactional` on writes. Lists sorted
  `Sort.by("id")`. Business rules and uniqueness are checked here and thrown as
  `ConflictException` (409) / `NotFoundException` (404) with messages like
  `"Vehicle with id 99 not found"`, `"Vehicle with VIN X already exists"`. A private
  `get<Entity>(id)` helper does the find-or-404. The DB unique constraint is only a race safety net.
- **Controller**: `@RestController @RequestMapping("/api/<plural>")`, constructor injection,
  `@Valid @RequestBody`. POST → `201 Created` + `Location` header (`ServletUriComponentsBuilder`),
  PUT → 200 with body, DELETE → `204 No Content`.
- **Errors**: everything goes through `GlobalExceptionHandler` as RFC 7807 `ProblemDetail`. 400 has
  `errors: [{field, message}]` (unknown enum → `"must be one of: …"`). Messages are English
  regardless of `Accept-Language`. Don't add per-controller handlers.
- **Migrations**: never edit an existing `V*.sql`; add the next number. Schema and seed data in separate
  migrations (`V4__customer.sql`, `V5__customer_seed.sql`). Constraint names `uq_<table>_<column>`,
  FKs `fk_<table>_<ref>`. Adding a NOT NULL column to a table with data: add, backfill, then set
  NOT NULL (see `V3__vehicle_fuel_type.sql`). `ddl-auto=validate` means entity and SQL must match.
- **Dates**: `LocalDate`, ISO strings in JSON. For rules relative to "today", inject a `Clock`
  so tests can fix the date.

### Backend tests

- `<Name>ControllerTest`: `@WebMvcTest(Controller.class)` + `@MockitoBean` service, MockMvc and
  `jsonPath`. Covers every endpoint: happy path, 404, 400 field errors, unknown enum, wrong type,
  non-JSON body, 409 (service conflict and `DataIntegrityViolationException`), English messages with
  `Accept-Language: pl`.
- `<Name>ServiceTest`: `@ExtendWith(MockitoExtension.class)`, `@Mock` repository, `@InjectMocks`
  service, AssertJ (`assertThat`, `assertThatThrownBy(...).hasMessage(...)`).
- `<Name>RepositoryTest`: `@DataJpaTest` with real Flyway schema, `TestEntityManager.flush()`;
  covers custom queries and save/read-back.
- Method names are sentences in camelCase: `createReturnsConflictProblemWhenVinAlreadyExists`.
  Body fixtures as text blocks (`VALID_BODY`), small `request()`/`response()` helpers.

## Frontend conventions

- Angular 22, standalone components, signals, no NgModules, no `.component` suffix in file names
  (`vehicle-list.ts` + `vehicle-list.html`, class `VehicleList`, selector `app-vehicle-list`).
- **API service** (`<entity>-api.ts`, `@Injectable({ providedIn: 'root' })`): exports the entity
  interface, `<Entity>Request = Omit<Entity, 'id'>`, enum as a string union + `<ENUM>_LABELS`
  record + `<ENUMS>` array. Reads use `httpResource` (`list()` with `defaultValue: []`,
  `get(id: Signal<number | undefined>)`), writes return `Observable`s from `HttpClient`.
  Nullable fields are `T | null`.
- **Routing**: `<plural>.routes.ts` with `''` list, `new` form, `:id/edit` form, `:id` detail;
  lazy-loaded from `app.routes.ts`; add a nav link in `app.html`. Route params come in as
  component inputs (`withComponentInputBinding`): `input.required({ transform: numberAttribute })`,
  or `optionalNumberAttribute` in the form.
- **Components**: `inject()`, `protected readonly` template members, writes via
  `await firstValueFrom(...)`, errors via `shared/problem-detail.ts` (`loadErrorMessage`,
  `problemDetailMessage`, `problemDetailToFormErrors`). Templates use `@if/@for/@empty`, show
  `Loading…`, errors in `<p class="error" role="alert">`, missing values as `—`.
- **Forms**: signal forms (`@angular/forms/signals`: `form`, `required`, `FormField`, `FormRoot`),
  model built by `toFormModel` via `linkedSignal`, converted by `toRequest` (empty string → `null`).
  Client-side validation is **only `required`**; formats, ranges and uniqueness are left to the
  backend and shown from its ProblemDetail. Field errors are shown when `touched()`.
- **Styling**: use plain `table`, `dl`, `form > label` like the vehicle templates; don't add CSS
  classes or component styles (`styles.css` already styles plain elements).
- **Tests**: vitest specs next to the component (`<name>-form.spec.ts`), `TestBed` with
  `provideHttpClient()`, `provideHttpClientTesting()`, `provideRouter([])`, `HttpTestingController`
  + `http.verify()`. Test the form like `vehicle-form.spec.ts`: required errors, create, edit,
  404 message, object-level (409) and field (400) backend errors.
- Prettier: single quotes, print width 100.

## Working rules

- Code, identifiers, UI texts and error messages in English; client-facing docs (INTENT, DECISIONS)
  may be in Polish.
- Run `./test.sh` before every commit; commit small and often (the git history is reviewed).
- Don't change the vehicle module unless a feature needs it (e.g. a new vehicle field); keep its
  existing tests green and extend them.
- Business rules belong in services and must be unit tested, including edge cases from `INTENT.md`
  (e.g. expiry date equal to the handover date is valid).

## Lessons

Append short, concrete entries here when something goes wrong or a non-obvious fact is learned
(what happened → what to do instead).

- Bash scripts (`test.sh`, `run.sh`, `mvnw`) need the Bash tool on this Windows machine; PowerShell
  can't run them directly.
- The brief and transcript contradict each other in places (e.g. re-verifying rejected documents):
  the API contract and the latest client decision win; record the choice in `INTENT.md`.
- Skill defaults must never override `INTENT.md`. First `feature-slice` run on customers invented a
  stricter phone regex than Z13 and spread email normalization over mapper and service; every rule
  must trace to its source, and normalization lives in one place (the Request record).
