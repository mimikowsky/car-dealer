# Field type mapping

One row per kind of field. Use it to fill the field spec table, then use the same decision in every
layer (SQL, entity, request, response, mapper, TS interface, form model, form input, tests).

| Kind | SQL | Entity (Java) | Request validation | TS type | Form model / input | List/detail display |
| --- | --- | --- | --- | --- | --- | --- |
| Required short text | `VARCHAR(n) NOT NULL` | `String`, `@Column(nullable = false, length = n)` | `@NotBlank @Size(max = n)` | `string` | `string`, `<input type="text">` | value |
| Optional short text | `VARCHAR(n)` | `String`, `@Column(length = n)` | `@Size(max = n)` (+ `@Pattern` if format) | `string \| null` | `string`, `<input type="text">`, `toRequest`: `model.x \|\| null` | `value ?? '—'` |
| Formatted code (VIN, NIP, postal code) | `VARCHAR(n)` exact length | `String` | `@Pattern(regexp = "...", message = "must be ...")` (+ `@NotBlank` if required) | `string` | `<input type="text">` | value |
| Email | `VARCHAR(255) NOT NULL` | `String` | `@NotBlank @Email @Size(max = 255)` | `string` | `<input type="email">` | value |
| Phone | `VARCHAR(20) NOT NULL` | `String` | `@NotBlank @Size(max = 20)`; add `@Pattern` only for what the source states, e.g. "digits, spaces, +, -" → `@Pattern(regexp = "[0-9 +-]*", message = "may contain only digits, spaces, + and -")`. No source → no pattern (assumption) | `string` | `<input type="tel">` | value |
| Long text | `VARCHAR(1000)` | `String`, `length = 1000` | `@Size(max = 1000)` | `string \| null` | `<textarea>` | value |
| Integer | `INTEGER NOT NULL` | `Integer` | `@NotNull` + `@Min/@Max/@Positive` | `number` | `number \| null`, `<input type="number">`, `toRequest`: `model.x!` | value |
| Decimal / money | `NUMERIC(12, 2) NOT NULL` | `BigDecimal`, `@Column(nullable = false, precision = 12, scale = 2)` | `@NotNull @DecimalMin("0.00") @Digits(integer = 10, fraction = 2)` | `number` | `number \| null`, `<input type="number" step="0.01">` | value |
| Required date | `DATE NOT NULL` | `LocalDate`, `@Column(nullable = false)` | `@NotNull` (+ `@PastOrPresent` / `@FutureOrPresent`) | `string` (ISO) | `string`, `<input type="date">` | value |
| Optional date | `DATE` | `LocalDate` | none (+ rule annotation) | `string \| null` | `string`, `<input type="date">`, `toRequest`: `model.x \|\| null` | `value ?? '—'` |
| Boolean flag | `BOOLEAN DEFAULT FALSE NOT NULL` | `boolean`, `@Column(nullable = false)` | none; record component is primitive `boolean` (missing → `false`) | `boolean` | `boolean`, `<input type="checkbox">` inside the label | `value ? 'Yes' : 'No'` |
| Enum | `VARCHAR(20) NOT NULL` | `@Enumerated(EnumType.STRING) @Column(nullable = false, length = 20)` + separate `enum` file | `@NotNull` | string union | `Type \| ''`, `<select>` with `<option value="">Select {label lowercase}</option>`, `toRequest`: `model.x as Type` | `LABELS[value]` |

## Notes

- The table gives defaults for when no source (contract, `INTENT.md`, description) says anything.
  A rule from a source replaces the default literally: never combine them or add extra
  restrictions (minimum lengths, character positions) the source doesn't state.
- Default lengths when the description gives none: names 50 (person names 100), codes = exact
  length, email 255, phone 20, free text 1000.
- Required strings use `@NotBlank`; never `@NotNull` alone for strings. Message comes from Bean
  Validation defaults (`must not be blank`), English thanks to the fixed locale.
- `@Pattern`, `@Email` and `@Size` accept `null`, so optional fields only carry the format rule.
  Remember a blank string `""` sent to a field with `@NotBlank` **and** `@Pattern` produces two errors
  for that field; `@Email` accepts `""`.
- Every custom `@Pattern` gets a human `message` (see `VehicleRequest.vin`).
- A rule that needs code (relative to the current year/date, cross-field) becomes a custom
  constraint annotation + `ConstraintValidator` like `@ProductionYear`, or a service check if it
  depends on other data (then it is a 409, not a 400).
- Uniqueness:
  - case-sensitive (codes, VIN): `existsByX(String)`, `existsByXAndIdNot(String, Long)`;
  - case-insensitive (email, usernames): normalize **exactly once, in the Request record's compact
    constructor** (`email = email == null ? null : email.trim().toLowerCase(Locale.ROOT);`).
    Everything downstream sees the normalized value: the repository uses plain `existsByX` /
    `existsByXAndIdNot`, and the service uses `request.x()` both for the check and the 409 message.
    The mapper and the service never normalize; no `IgnoreCase` queries. The DB `uq_` constraint
    then enforces uniqueness for real.
  - Always also add `CONSTRAINT uq_{table}_{column} UNIQUE ({column})` in SQL.
  - Optional unique fields: only check when the value is not `null`.
- Column naming: snake_case of the field (`plannedHandoverDate` → `planned_handover_date`), aligned
  with spaces in the `CREATE TABLE` like `V1__vehicle.sql`.
- H2 reserved words cannot be table or column names: `user`, `order`, `value`, `values`, `key`,
  `year`, `month`, `day`, `hour`, `minute`, `second`, `limit`, `offset`, `table`, `row`, `group`,
  `from`, `to`, `end`, `default`, `interval`. Keep the Java/JSON name and map it with
  `@Column(name = "...")` to a safe column (e.g. field `year` → column `model_year`); for a table use
  `@Table(name = "app_user")` etc.
- Reference to another entity by id (not nested URL): `Long {other}Id` in request/response,
  `@ManyToOne(optional = false, fetch = FetchType.LAZY) @JoinColumn(name = "{other}_id")` in the
  entity, `BIGINT NOT NULL` + `CONSTRAINT fk_{table}_{other} FOREIGN KEY ({other}_id) REFERENCES {other} (id)`.
  The service loads the referenced entity and throws `NotFoundException` if missing.
  For URLs nested under a parent see [child-resource.md](child-resource.md).
