# Frontend templates

Placeholders as in [backend.md](backend.md), plus `{fe-file}` (singular kebab, e.g. `customer`),
`{fe-folder}` (plural kebab, e.g. `customers`), `{Label}` / `{Labels}` (UI text, e.g. `Customer`),
`{label}` (lowercase UI text). Folder: `frontend/src/app/{fe-folder}/`.
Start from a copy of the matching vehicle file and replace names and fields; the snippets below show
only what changes per entity.

## `{fe-file}-api.ts`

```ts
import { HttpClient, httpResource } from '@angular/common/http';
import { Injectable, Signal, inject } from '@angular/core';
import { Observable } from 'rxjs';

// per enum field:
export type CustomerType = 'PRIVATE' | 'COMPANY';
export const CUSTOMER_TYPE_LABELS: Record<CustomerType, string> = { PRIVATE: 'Private', COMPANY: 'Company' };
export const CUSTOMER_TYPES = Object.keys(CUSTOMER_TYPE_LABELS) as CustomerType[];

export interface {Entity} {
  id: number;
  firstName: string;        // each field, TS type from field-types.md, optional → `T | null`
  nip: string | null;
}

export type {Entity}Request = Omit<{Entity}, 'id'>;

@Injectable({ providedIn: 'root' })
export class {Entity}Api {
  private readonly http = inject(HttpClient);
  private readonly url = '/api/{url}';
  // list(), get(id), create(), update(), delete(): identical to VehicleApi
}
```

## `{fe-folder}.routes.ts`

```ts
export const {entities}Routes: Routes = [
  { path: '', component: {Entity}List },
  { path: 'new', component: {Entity}Form },
  { path: ':id/edit', component: {Entity}Form },
  { path: ':id', component: {Entity}Detail },
];
```

`app.routes.ts`: add after the vehicles entry

```ts
  {
    path: '{fe-folder}',
    loadChildren: () => import('./{fe-folder}/{fe-folder}.routes').then((m) => m.{entities}Routes),
  },
```

`app.html`: add `<a routerLink="/{fe-folder}" routerLinkActive="active">{Labels}</a>` after the
existing links inside `<nav>`.

## List (`{fe-file}-list.ts/.html`)

Copy `vehicle-list`. Changes:
- `loadErrorMessage(error, '{Labels} not found')`.
- Heading `<h1>{Labels}</h1>`, link `Add {label}`.
- One `<th>` per displayed field (skip long text), last column `Actions`. First column is the key
  field (or the most identifying one) as a `routerLink` to the detail.
- Cell display per field-types.md (`?? '—'` for optional, labels for enums, `Yes/No` for booleans).
- `@empty` row: `colspan` = number of columns, text `No {labels} yet.`
- Expose label records used by the template as `protected readonly`.

## Detail (`{fe-file}-detail.ts/.html`)

Copy `vehicle-detail`. `<p><a routerLink="/{fe-folder}">Back to {labels}</a></p>`, `<h1>` with the
most human field(s) (e.g. `{{ customer.firstName }} {{ customer.lastName }}`), a `<dl>` with every
field (`<dt>` label, `<dd>` display), Edit link. Do not copy the vehicle "Documents" section.

## Form (`{fe-file}-form.ts/.html`)

`{Entity}FormModel` interface: strings as `string` (optional ones too), numbers `number | null`,
enums `Type | ''`, booleans `boolean`, dates `string`.

```ts
function toFormModel(customer: Customer | undefined): CustomerFormModel {
  return {
    firstName: customer?.firstName ?? '',
    nip: customer?.nip ?? '',
    active: customer?.active ?? false,
    rating: customer?.rating ?? null,
    type: customer?.type ?? '',
  };
}

function toRequest(model: CustomerFormModel): CustomerRequest {
  return {
    firstName: model.firstName,
    nip: model.nip || null,          // every optional string/date
    active: model.active,
    rating: model.rating!,           // required number
    type: model.type as CustomerType,
  };
}
```

Validators: one `required(path.x, { message: '{Field label} is required' })` per required field, in
field order. Nothing else. Submission/navigation/`problemDetailToFormErrors` identical to
`VehicleForm`, navigating to `['/{fe-folder}', saved.id]`. Not-found text `'{Entity} not found'`.

Template: copy `vehicle-form.html`; heading `Edit {label}` / `New {label}`; one block per field:

```html
    <label>
      First name
      <input type="text" [formField]="customerForm.firstName" />
    </label>
    @if (customerForm.firstName().touched()) {
      @for (error of customerForm.firstName().errors(); track error) {
        <p class="error" role="alert">{{ error.message }}</p>
      }
    }
```

Checkbox: `<label><input type="checkbox" [formField]="form.active" /> Active</label>`.
Select: as the fuel type select, with `<option value="">Select {field label lowercase}</option>`.
Cancel link: `isEdit() ? ['/{fe-folder}', id()] : ['/{fe-folder}']`.

## Form spec (`{fe-file}-form.spec.ts`)

Copy `vehicle-form.spec.ts` (same `beforeEach`, `afterEach(() => http.verify())`, helpers `fill`,
`select`, `submit`, `errors`). Inputs are found by `input[type="..."]` and index **in DOM order per
type** — recount after writing the template (`email`, `tel`, `date` are separate types from `text`).
Checkbox: `element.querySelectorAll<HTMLInputElement>('input[type="checkbox"]')[i].click()`.

`fillValid{Entity}()` fills only the required fields. Tests, all mandatory:

1. `shows required errors and sends nothing when the form is empty` — `http.expectNone('/api/{url}')`,
   exact list of required messages in field order.
2. `leaves format and range rules to the backend` — fill an invalid format value (e.g. email
   `not-an-email`), POST is sent with that value, flush a 400 with field errors, errors shown
   (displayed in DOM/field order, not response order).
3. `creates the {label} and navigates to its details` — fill all fields including optional ones,
   `expect(request.request.body).toEqual({...})` with **every** field, navigate to `['/{fe-folder}', 7]`.
   Add a variant assertion (or separate test) that empty optional fields are sent as `null`.
4. `loads the {label} in edit mode and updates it` — `setInput('id', '3')`, flush GET, change one field,
   PUT body `toEqual` the full entity without id.
5. `shows a dedicated message when the {label} to edit does not exist` — 404 → `'{Entity} not found'`.
6. `shows the ProblemDetail detail when loading the {label} fails` — 503 → detail text.
7. `shows object-level errors returned by the backend above the form` — 400 with `field: null`.
8. `shows field errors returned by the backend as ProblemDetail`.
9. `shows the ProblemDetail detail when the backend reports a conflict` — 409 with the real conflict
   message of the unique field (or a generic `Request conflicts with existing data` if none).
