---
name: feature-slice
description: Generate a complete vertical feature slice (Spring Boot backend + Angular frontend + tests) for a new entity in this dealership project, following the vehicle module exactly. Use when asked to add/generate/scaffold a new entity, resource, CRUD, module or "feature slice" (e.g. "customers with name, email, phone"), including entities nested under another resource (e.g. documents of a vehicle).
---

# feature-slice

Turns a short entity description into a working, tested slice that is indistinguishable in style
from the vehicle module: Flyway migrations, JPA entity, request/response records, mapper,
repository, service, controller, backend tests, Angular API service, routes, list/detail/form
components, form spec and nav link. The run is finished only when all tests pass.

The vehicle module is the source of truth. The reference files in this skill are templates
distilled from it; when a template and the real vehicle code disagree, the vehicle code wins.

## Inputs

The user gives an entity name and fields, usually informally ("Customer: first name, last name,
email unique, phone, optional NIP for companies"). It may also give business rules, an API
contract, or point to `INTENT.md` / `BRIEF.md`. Read those if they mention the entity: a fixed API
contract (paths, enum names, status codes) always wins over this skill's defaults.

Do not stop to ask questions. Where the description is ambiguous, choose the most conservative
option consistent with the vehicle module, write it down as an assumption, and continue.

## Workflow

Follow the steps in order. Keep a task list with these steps.

### 1. Read the pattern (always, even if you think you know it)

Read these files fully before writing anything:

- `backend/src/main/java/com/example/dealership/vehicle/` — all files
- `backend/src/test/java/com/example/dealership/vehicle/` — all three tests
- `backend/src/main/resources/db/migration/` — list files, read the highest-numbered ones
- `frontend/src/app/vehicles/` — all files, especially `vehicle-form.spec.ts`
- `frontend/src/app/app.routes.ts`, `frontend/src/app/app.html`, `frontend/src/app/shared/*.ts`
- `CLAUDE.md` (conventions, lessons) and, if present, `INTENT.md`

Then read this skill's references: [reference/field-types.md](reference/field-types.md),
[reference/backend.md](reference/backend.md), [reference/frontend.md](reference/frontend.md).
For an entity nested under another resource also read
[reference/child-resource.md](reference/child-resource.md).

### 2. Write the field spec (before any code)

Produce this table in your reply and keep it as the single source for every layer:

| field (camelCase) | label | Java type | SQL column | required | constraints | unique | TS type | form input |
| --- | --- | --- | --- | --- | --- | --- | --- | --- |

Plus the naming block:

```
Entity        Customer                 (Java class, TS interface)
entity        customer                 (camelCase variable)
Entities      Customers                (plural, used in test/method names)
entities      customers                (plural camelCase)
package       customer                 (Java package: lowercase, no separators)
table         customer                 (SQL table: singular snake_case)
url           /api/customers           (plural kebab-case)
fe-folder     frontend/src/app/customers/   (plural kebab-case)
fe-file       customer                 (singular kebab-case prefix: customer-api.ts, customer-list.ts)
label         Customer / Customers     (UI texts, English)
```

Map every field with [reference/field-types.md](reference/field-types.md). Decide explicitly, per field:
required or optional, max length, format rule (pattern/email/range/date rule), uniqueness and
whether uniqueness is case-insensitive (emails: yes). Choose a "natural key" for 409 messages
(the unique field; if none, there is no 409 in the service).

List assumptions right under the table (e.g. "NIP optional, exactly 10 digits, no checksum").

### 3. Backend

Create, in this order (templates in [reference/backend.md](reference/backend.md)):

1. `V{n}__{table}.sql` (schema) and `V{n+1}__{table}_seed.sql` (2–3 realistic rows that pass
   validation) where `n` = highest existing version + 1. Never edit existing migrations.
2. Enum(s) for enumerated fields, one file each.
3. Custom constraint annotation + validator only when a rule cannot be expressed with standard
   Bean Validation (like `@ProductionYear`).
4. `{Entity}.java`, `{Entity}Request.java`, `{Entity}Response.java`, `{Entity}Mapper.java`,
   `{Entity}Repository.java`, `{Entity}Service.java`, `{Entity}Controller.java`.
5. Tests: `{Entity}ControllerTest`, `{Entity}ServiceTest`, `{Entity}RepositoryTest` covering every
   case in the checklist in [reference/backend.md](reference/backend.md#test-checklist).

Run `cd backend && ./mvnw test` (Bash tool) and fix until green. `@DataJpaTest` runs all Flyway
migrations, so a broken migration or entity/column mismatch shows up here.

### 4. Frontend

Create (templates in [reference/frontend.md](reference/frontend.md)):

1. `{fe-file}-api.ts`, `{fe-folder}.routes.ts` (named `{entities}Routes`), `{fe-file}-list.ts/.html`,
   `{fe-file}-detail.ts/.html`, `{fe-file}-form.ts/.html`, `{fe-file}-form.spec.ts`.
2. Lazy route in `app.routes.ts` and a nav link in `app.html` (after existing links).

Run `cd frontend && npm test` and `npm run build` (template type-check), then
`npx prettier --write "src/app/{fe-folder}/**" src/app/app.routes.ts src/app/app.html`. Fix until green.

### 5. Verify and report

1. Run `./test.sh` from the repository root (Bash tool). It must print PASSED for both parts.
2. Self-review against the [final checklist](#final-checklist) below.
3. Report: the field spec, assumptions, list of created/changed files, test results. Do not
   commit unless the user asked for it.

## Hard rules

- Copy structure, naming, annotation order, import grouping and test style from the vehicle module.
  No Lombok, no MapStruct, no new dependencies, no new CSS, no NgModules, no `.component` suffix.
- Do not modify the vehicle module, `GlobalExceptionHandler` or `shared/` unless the user's
  description requires it (e.g. a new field on Vehicle). If you must, extend existing tests too.
- Every uniqueness rule is enforced twice: a service check that throws `ConflictException`
  (on create *and* update, excluding the entity itself) and a DB constraint `uq_{table}_{column}`.
- Bean Validation `@Size(max)` equals the SQL column length; nullability matches `NOT NULL`.
- Frontend validates only `required` (with `"{Label} is required"` messages); all format, range
  and uniqueness errors come from the backend ProblemDetail.
- Optional text/date/number fields are sent as `null`, never `""`, from the form.
- All texts in English. Error messages: `"{Entity} with id {id} not found"`,
  `"{Entity} with {field label lowercase} {value} already exists"`.
- Generate only the endpoints the description implies. Default is full CRUD
  (list, get, create, update, delete). If the contract lists fewer, generate fewer and drop the
  matching tests/UI.

## Final checklist

- [ ] Migration numbers continue the sequence; schema and seed in separate files; seed passes validation
- [ ] Entity columns, lengths, nullability and enums match the SQL exactly (`ddl-auto=validate`)
- [ ] Request has validation for every field; optional fields have no `@NotNull/@NotBlank`
- [ ] Service: `readOnly` class transaction, `@Transactional` writes, `Sort.by("id")`, `get{Entity}` helper, 409 for each unique field on create and update
- [ ] Controller: 201 + Location on POST, 200 on PUT, 204 on DELETE
- [ ] Controller test covers every item of the test checklist; service test covers every branch; repository test covers every custom query
- [ ] Frontend: api with types + labels for enums, 4 routes, list with delete + error, detail with `dl`, form with required validators and `problemDetailToFormErrors`, nav link
- [ ] Form spec covers: required errors, backend-only rules, create, edit, 404, load error, object-level error, field error, 409
- [ ] `./test.sh` passes; prettier applied
