import { HttpClient, HttpParams } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { BatchOption, FermentationReading, ReadingKind, RecordReadingRequest } from '../domain/reading.model';

@Injectable({ providedIn: 'root' })
export class ReadingsApi {
  private readonly http = inject(HttpClient);
  private readonly baseUrl = '/api/v1/fermentation/readings';

  /** Lotes em que se pode anexar leituras; só o necessário para o seletor. */
  batches() {
    return this.http.get<BatchOption[]>('/api/v1/production/batches');
  }

  list(batchId: string, kind: ReadingKind | null) {
    let params = new HttpParams().set('batchId', batchId);
    if (kind) {
      params = params.set('kind', kind);
    }
    return this.http.get<FermentationReading[]>(this.baseUrl, { params });
  }

  record(request: RecordReadingRequest) {
    return this.http.post<{ id: string; valid: boolean; invalidReason: string }>(this.baseUrl, request);
  }
}
