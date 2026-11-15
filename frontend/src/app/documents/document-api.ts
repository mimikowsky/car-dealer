import { HttpClient, httpResource } from '@angular/common/http';
import { Injectable, Signal, inject } from '@angular/core';
import { Observable } from 'rxjs';

export type DocumentType = 'SALES_AGREEMENT' | 'INVOICE' | 'COC' | 'OC_POLICY' | 'REGISTRATION_POA';

export const DOCUMENT_TYPE_LABELS: Record<DocumentType, string> = {
  SALES_AGREEMENT: 'Sales agreement',
  INVOICE: 'Invoice',
  COC: 'Certificate of conformity (COC)',
  OC_POLICY: 'OC insurance policy',
  REGISTRATION_POA: 'Registration power of attorney',
};

export const DOCUMENT_TYPES = Object.keys(DOCUMENT_TYPE_LABELS) as DocumentType[];

export type DocumentStatus = 'PENDING' | 'VERIFIED' | 'REJECTED';

export const DOCUMENT_STATUS_LABELS: Record<DocumentStatus, string> = {
  PENDING: 'Pending',
  VERIFIED: 'Verified',
  REJECTED: 'Rejected',
};

export const DOCUMENT_STATUSES = Object.keys(DOCUMENT_STATUS_LABELS) as DocumentStatus[];

export interface VehicleDocument {
  id: number;
  vehicleId: number;
  type: DocumentType;
  number: string;
  issueDate: string;
  expiryDate: string | null;
  status: DocumentStatus;
}

// The vehicle id comes from the URL and the status is always PENDING for a new document.
export type DocumentRequest = Omit<VehicleDocument, 'id' | 'vehicleId' | 'status'>;

@Injectable({ providedIn: 'root' })
export class DocumentApi {
  private readonly http = inject(HttpClient);

  list(vehicleId: Signal<number | undefined>) {
    return httpResource<VehicleDocument[]>(
      () => {
        const id = vehicleId();
        return id === undefined ? undefined : this.url(id);
      },
      { defaultValue: [] },
    );
  }

  create(vehicleId: number, request: DocumentRequest): Observable<VehicleDocument> {
    return this.http.post<VehicleDocument>(this.url(vehicleId), request);
  }

  changeStatus(vehicleId: number, id: number, status: DocumentStatus): Observable<VehicleDocument> {
    return this.http.put<VehicleDocument>(`${this.url(vehicleId)}/${id}/status`, { status });
  }

  private url(vehicleId: number): string {
    return `/api/vehicles/${vehicleId}/documents`;
  }
}
