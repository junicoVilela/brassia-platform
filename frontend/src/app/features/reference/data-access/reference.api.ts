import { HttpClient } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import {
  ReferenceDataset,
  ReferenceSource,
  RecordReferenceDatasetRequest,
  RegisterReferenceSourceRequest,
} from '../domain/reference.model';

interface PageResponse<T> {
  content: T[];
  page: number;
  size: number;
  totalElements: number;
  totalPages: number;
}

@Injectable({ providedIn: 'root' })
export class ReferenceApi {
  private readonly http = inject(HttpClient);
  private readonly baseUrl = '/api/v1/reference';

  listSources() {
    return this.http.get<PageResponse<ReferenceSource>>(`${this.baseUrl}/sources`, {
      params: { page: '0', size: '100' },
    });
  }

  registerSource(request: RegisterReferenceSourceRequest) {
    return this.http.post<{ id: string }>(`${this.baseUrl}/sources`, request);
  }

  listDatasets(sourceId: string) {
    return this.http.get<ReferenceDataset[]>(`${this.baseUrl}/sources/${sourceId}/datasets`);
  }

  recordDataset(sourceId: string, request: RecordReferenceDatasetRequest) {
    return this.http.post<ReferenceDataset>(`${this.baseUrl}/sources/${sourceId}/datasets`, request);
  }

  publishDataset(datasetId: string) {
    return this.http.post<ReferenceDataset>(`${this.baseUrl}/datasets/${datasetId}/publish`, {});
  }
}
