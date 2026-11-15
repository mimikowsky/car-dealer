import { Component, inject, input, signal } from '@angular/core';
import { FormField, FormRoot, form, required } from '@angular/forms/signals';
import { firstValueFrom } from 'rxjs';
import {
  loadErrorMessage,
  problemDetailMessage,
  problemDetailToFormErrors,
} from '../shared/problem-detail';
import {
  DOCUMENT_STATUS_LABELS,
  DOCUMENT_TYPES,
  DOCUMENT_TYPE_LABELS,
  DocumentApi,
  DocumentRequest,
  DocumentStatus,
  DocumentType,
  VehicleDocument,
} from './document-api';

interface DocumentFormModel {
  type: DocumentType | '';
  number: string;
  issueDate: string;
  expiryDate: string;
}

function toFormModel(): DocumentFormModel {
  return { type: '', number: '', issueDate: '', expiryDate: '' };
}

function toRequest(model: DocumentFormModel): DocumentRequest {
  return {
    type: model.type as DocumentType,
    number: model.number,
    issueDate: model.issueDate,
    expiryDate: model.expiryDate || null,
  };
}

@Component({
  selector: 'app-document-section',
  imports: [FormField, FormRoot],
  templateUrl: './document-section.html',
})
export class DocumentSection {
  private readonly documentApi = inject(DocumentApi);

  readonly vehicleId = input.required<number>();

  protected readonly documents = this.documentApi.list(this.vehicleId);
  protected readonly documentTypes = DOCUMENT_TYPES;
  protected readonly documentTypeLabels = DOCUMENT_TYPE_LABELS;
  protected readonly documentStatusLabels = DOCUMENT_STATUS_LABELS;
  protected readonly actionError = signal<string | null>(null);

  private readonly model = signal(toFormModel());

  protected readonly documentForm = form(
    this.model,
    (path) => {
      required(path.type, { message: 'Type is required' });
      required(path.number, { message: 'Number is required' });
      required(path.issueDate, { message: 'Issue date is required' });
    },
    {
      submission: {
        action: async (documentForm) => {
          const request = toRequest(documentForm().value());
          try {
            await firstValueFrom(this.documentApi.create(this.vehicleId(), request));
            documentForm().reset(toFormModel());
            this.documents.reload();
            return undefined;
          } catch (error) {
            return problemDetailToFormErrors(error, documentForm);
          }
        },
      },
    },
  );

  protected loadError(error: unknown): string {
    return loadErrorMessage(error, 'Documents not found');
  }

  protected async changeStatus(document: VehicleDocument, status: DocumentStatus): Promise<void> {
    this.actionError.set(null);
    try {
      await firstValueFrom(this.documentApi.changeStatus(this.vehicleId(), document.id, status));
      this.documents.reload();
    } catch (error) {
      this.actionError.set(problemDetailMessage(error));
    }
  }
}
