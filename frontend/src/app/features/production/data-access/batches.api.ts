import { HttpClient } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { BatchAlert, CreateAlertRequest } from '../domain/alert.model';
import { Batch } from '../domain/batch.model';
import { BrewCorrection, CorrectionResult, PreviewCorrectionRequest } from '../domain/correction.model';
import { Measurement, RecordMeasurementRequest } from '../domain/measurement.model';
import { Transfer, TransferRequest } from '../domain/transfer.model';

@Injectable({ providedIn: 'root' })
export class BatchesApi {
  private readonly http = inject(HttpClient);
  private readonly baseUrl = '/api/v1/production/batches';

  list() {
    return this.http.get<Batch[]>(this.baseUrl);
  }

  get(batchId: string) {
    return this.http.get<Batch>(`${this.baseUrl}/${batchId}`);
  }

  completeStep(batchId: string, stepId: string) {
    return this.http.post<Batch>(`${this.baseUrl}/${batchId}/steps/${stepId}/complete`, {});
  }

  measurements(batchId: string) {
    return this.http.get<Measurement[]>(`${this.baseUrl}/${batchId}/measurements`);
  }

  recordMeasurement(batchId: string, request: RecordMeasurementRequest) {
    return this.http.post<{ id: string }>(`${this.baseUrl}/${batchId}/measurements`, request);
  }

  corrections() {
    return this.http.get<BrewCorrection[]>('/api/v1/production/corrections');
  }

  previewCorrection(batchId: string, request: PreviewCorrectionRequest) {
    return this.http.post<CorrectionResult>(`${this.baseUrl}/${batchId}/corrections/preview`, request);
  }

  transfer(batchId: string, request: TransferRequest) {
    return this.http.post<Transfer>(`${this.baseUrl}/${batchId}/transfer`, request);
  }

  alerts(batchId: string) {
    return this.http.get<BatchAlert[]>(`${this.baseUrl}/${batchId}/alerts`);
  }

  createAlert(batchId: string, request: CreateAlertRequest) {
    return this.http.post<{ id: string }>(`${this.baseUrl}/${batchId}/alerts`, request);
  }

  confirmAlert(batchId: string, alertId: string) {
    return this.http.post<BatchAlert>(`${this.baseUrl}/${batchId}/alerts/${alertId}/confirm`, {});
  }
}
