import { HttpClient, HttpParams } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { FermentationProfile } from '../domain/profile.model';
import {
  BatchOption,
  FermentationReading,
  FgStability,
  ReadingKind,
  RecordReadingRequest,
} from '../domain/reading.model';

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

  /** Perfis publicados; só eles podem reger um parecer de estabilidade. */
  profiles() {
    return this.http.get<FermentationProfile[]>('/api/v1/fermentation/profiles');
  }

  fgStability(batchId: string, profileId: string) {
    return this.http.get<FgStability>(`/api/v1/fermentation/batches/${batchId}/fg-stability`, {
      params: new HttpParams().set('profileId', profileId),
    });
  }

  record(request: RecordReadingRequest) {
    return this.http.post<{ id: string; valid: boolean; invalidReason: string }>(this.baseUrl, request);
  }
}
