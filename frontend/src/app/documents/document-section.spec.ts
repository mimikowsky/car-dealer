import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';
import { DocumentSection } from './document-section';

const URL = '/api/vehicles/1/documents';

const PENDING_INVOICE = {
  id: 5,
  vehicleId: 1,
  type: 'INVOICE',
  number: 'FV/2026/10/118',
  issueDate: '2026-10-05',
  expiryDate: null,
  status: 'PENDING',
};

const VERIFIED_POLICY = {
  id: 6,
  vehicleId: 1,
  type: 'OC_POLICY',
  number: 'OC-7781-2026',
  issueDate: '2026-10-20',
  expiryDate: '2027-10-19',
  status: 'VERIFIED',
};

describe('DocumentSection', () => {
  let fixture: ComponentFixture<DocumentSection>;
  let element: HTMLElement;
  let http: HttpTestingController;

  beforeEach(async () => {
    TestBed.configureTestingModule({
      providers: [provideHttpClient(), provideHttpClientTesting(), provideRouter([])],
    });
    fixture = TestBed.createComponent(DocumentSection);
    fixture.componentRef.setInput('vehicleId', 1);
    element = fixture.nativeElement;
    http = TestBed.inject(HttpTestingController);
    fixture.detectChanges();
  });

  afterEach(() => http.verify());

  async function flushList(documents: object[]): Promise<void> {
    http.expectOne({ method: 'GET', url: URL }).flush(documents);
    await fixture.whenStable();
  }

  function fill(type: string, index: number, value: string): void {
    const input = element.querySelectorAll<HTMLInputElement>(`input[type="${type}"]`)[index];
    input.value = value;
    input.dispatchEvent(new Event('input'));
  }

  function select(value: string): void {
    const select = element.querySelector('select')!;
    select.value = value;
    select.dispatchEvent(new Event('input'));
  }

  function fillValidDocument(): void {
    select('INVOICE');
    fill('text', 0, 'FV/2026/10/118');
    fill('date', 0, '2026-10-05');
  }

  async function submit(): Promise<void> {
    element.querySelector('form')!.dispatchEvent(new Event('submit'));
    await fixture.whenStable();
  }

  function errors(): string[] {
    return Array.from(element.querySelectorAll('.error'), (error) => error.textContent!.trim());
  }

  function rows(): string[] {
    return Array.from(element.querySelectorAll('tbody tr'), (row) =>
      Array.from(row.querySelectorAll('td:not(:has(button))'), (cell) =>
        cell.textContent!.replace(/\s+/g, ' ').trim(),
      )
        .filter((text) => text !== '')
        .join(' '),
    );
  }

  function button(label: string): HTMLButtonElement {
    return Array.from(element.querySelectorAll<HTMLButtonElement>('tbody button')).find(
      (button) => button.textContent!.trim() === label,
    )!;
  }

  it('renders the documents of the vehicle', async () => {
    await flushList([PENDING_INVOICE, VERIFIED_POLICY]);

    expect(rows()).toEqual([
      'Invoice FV/2026/10/118 2026-10-05 — Pending',
      'OC insurance policy OC-7781-2026 2026-10-20 2027-10-19 Verified',
    ]);
    expect(
      Array.from(element.querySelectorAll('tbody button'), (button) => button.textContent!.trim()),
    ).toEqual(['Verify', 'Reject']);
  });

  it('shows an empty row when the vehicle has no documents', async () => {
    await flushList([]);

    expect(rows()).toEqual(['No documents yet.']);
  });

  it('shows the ProblemDetail detail when loading the documents fails', async () => {
    http
      .expectOne({ method: 'GET', url: URL })
      .flush(
        { title: 'Service Unavailable', status: 503, detail: 'Database is unavailable' },
        { status: 503, statusText: 'Service Unavailable' },
      );

    await vi.waitFor(() => expect(errors()).toEqual(['Database is unavailable']));
  });

  it('shows required errors and sends nothing when the form is empty', async () => {
    await flushList([]);

    await submit();

    http.expectNone({ method: 'POST', url: URL });
    expect(errors()).toEqual(['Type is required', 'Number is required', 'Issue date is required']);
  });

  it('creates the document, resets the form and reloads the list', async () => {
    await flushList([]);
    fillValidDocument();
    fill('date', 1, '2027-10-04');

    await submit();
    const request = http.expectOne({ method: 'POST', url: URL });
    expect(request.request.body).toEqual({
      type: 'INVOICE',
      number: 'FV/2026/10/118',
      issueDate: '2026-10-05',
      expiryDate: '2027-10-04',
    });
    request.flush({ ...PENDING_INVOICE, expiryDate: '2027-10-04' });

    await vi.waitFor(() => http.expectOne({ method: 'GET', url: URL }).flush([PENDING_INVOICE]));
    await fixture.whenStable();
    expect(rows()).toEqual(['Invoice FV/2026/10/118 2026-10-05 — Pending']);
    expect(element.querySelector('select')!.value).toBe('');
    expect(element.querySelector<HTMLInputElement>('input[type="text"]')!.value).toBe('');
    expect(errors()).toEqual([]);
  });

  it('sends an empty expiry date as null', async () => {
    await flushList([]);
    fillValidDocument();

    await submit();
    const request = http.expectOne({ method: 'POST', url: URL });
    expect(request.request.body.expiryDate).toBeNull();
    request.flush(PENDING_INVOICE);

    await vi.waitFor(() => http.expectOne({ method: 'GET', url: URL }).flush([PENDING_INVOICE]));
  });

  it('leaves date rules to the backend and shows its field errors', async () => {
    await flushList([]);
    fillValidDocument();
    fill('date', 0, '2999-01-01');
    fill('date', 1, '2998-01-01');

    await submit();
    const request = http.expectOne({ method: 'POST', url: URL });
    expect(request.request.body.issueDate).toBe('2999-01-01');
    request.flush(
      {
        title: 'Bad Request',
        status: 400,
        detail: 'Validation failed',
        errors: [
          { field: 'expiryDate', message: 'must not be before issue date' },
          { field: 'issueDate', message: 'must be a date in the past or in the present' },
        ],
      },
      { status: 400, statusText: 'Bad Request' },
    );

    await vi.waitFor(() =>
      expect(errors()).toEqual([
        'must be a date in the past or in the present',
        'must not be before issue date',
      ]),
    );
  });

  it('shows the ProblemDetail detail when the backend reports a conflict', async () => {
    await flushList([PENDING_INVOICE]);
    fillValidDocument();

    await submit();
    http.expectOne({ method: 'POST', url: URL }).flush(
      {
        title: 'Conflict',
        status: 409,
        detail: 'Vehicle with id 1 already has an active INVOICE document',
      },
      { status: 409, statusText: 'Conflict' },
    );

    await vi.waitFor(() =>
      expect(errors()).toEqual(['Vehicle with id 1 already has an active INVOICE document']),
    );
  });

  it('verifies a pending document and reloads the list', async () => {
    await flushList([PENDING_INVOICE]);

    button('Verify').click();
    const request = http.expectOne({ method: 'PUT', url: `${URL}/5/status` });
    expect(request.request.body).toEqual({ status: 'VERIFIED' });
    request.flush({ ...PENDING_INVOICE, status: 'VERIFIED' });

    await vi.waitFor(() =>
      http
        .expectOne({ method: 'GET', url: URL })
        .flush([{ ...PENDING_INVOICE, status: 'VERIFIED' }]),
    );
    await fixture.whenStable();
    expect(rows()).toEqual(['Invoice FV/2026/10/118 2026-10-05 — Verified']);
  });

  it('rejects a pending document', async () => {
    await flushList([PENDING_INVOICE]);

    button('Reject').click();
    const request = http.expectOne({ method: 'PUT', url: `${URL}/5/status` });
    expect(request.request.body).toEqual({ status: 'REJECTED' });
    request.flush({ ...PENDING_INVOICE, status: 'REJECTED' });

    await vi.waitFor(() =>
      http
        .expectOne({ method: 'GET', url: URL })
        .flush([{ ...PENDING_INVOICE, status: 'REJECTED' }]),
    );
  });

  it('shows the ProblemDetail detail when a status change is refused', async () => {
    await flushList([PENDING_INVOICE]);

    button('Verify').click();
    http.expectOne({ method: 'PUT', url: `${URL}/5/status` }).flush(
      {
        title: 'Conflict',
        status: 409,
        detail: 'Document with id 5 cannot change status from REJECTED to VERIFIED',
      },
      { status: 409, statusText: 'Conflict' },
    );

    await vi.waitFor(() =>
      expect(errors()).toEqual([
        'Document with id 5 cannot change status from REJECTED to VERIFIED',
      ]),
    );
  });
});
