import { HttpClient } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import {
  CreateWaterReferenceProfileRequest,
  RecordWaterReportRequest,
  RegisterWaterSourceRequest,
  WaterReferenceProfile,
  WaterReport,
  WaterSource,
} from '../domain/water.model';

interface PageResponse<T> {
  content: T[];
  page: number;
  size: number;
  totalElements: number;
  totalPages: number;
}

@Injectable({ providedIn: 'root' })
export class WaterApi {
  private readonly http = inject(HttpClient);
  private readonly baseUrl = '/api/v1/water/sources';

  listSources(page = 0, size = 100) {
    return this.http.get<PageResponse<WaterSource>>(this.baseUrl, { params: { page, size } });
  }

  createSource(request: RegisterWaterSourceRequest) {
    return this.http.post<WaterSource>(this.baseUrl, request);
  }

  listReports(sourceId: string) {
    return this.http.get<WaterReport[]>(`${this.baseUrl}/${sourceId}/reports`);
  }

  recordReport(sourceId: string, request: RecordWaterReportRequest) {
    return this.http.post<WaterReport>(`${this.baseUrl}/${sourceId}/reports`, request);
  }

  listReferenceProfiles() {
    return this.http.get<PageResponse<WaterReferenceProfile>>('/api/v1/water/reference-profiles', {
      params: { page: 0, size: 100 },
    });
  }

  createReferenceProfile(request: CreateWaterReferenceProfileRequest) {
    return this.http.post<{ id: string; status: string }>('/api/v1/water/reference-profiles', request);
  }

  publishReferenceProfile(id: string) {
    return this.http.post<{ status: string }>(`/api/v1/water/reference-profiles/${id}/publish`, {});
  }
}
