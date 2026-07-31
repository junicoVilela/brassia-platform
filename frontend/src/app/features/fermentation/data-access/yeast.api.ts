import { HttpClient, HttpParams } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { BatchOption } from '../domain/reading.model';
import { CollectYeastRequest, YeastHarvest } from '../domain/yeast.model';

@Injectable({ providedIn: 'root' })
export class YeastApi {
  private readonly http = inject(HttpClient);
  private readonly baseUrl = '/api/v1/fermentation/yeast/harvests';

  list(onlyAvailable: boolean) {
    return this.http.get<YeastHarvest[]>(this.baseUrl, {
      params: new HttpParams().set('onlyAvailable', onlyAvailable),
    });
  }

  genealogy(harvestId: string) {
    return this.http.get<YeastHarvest[]>(`${this.baseUrl}/${harvestId}/genealogy`);
  }

  collect(request: CollectYeastRequest) {
    return this.http.post<{ id: string; generation: number }>(this.baseUrl, request);
  }

  review(harvestId: string, approve: boolean, note: string | null) {
    return this.http.post<void>(`${this.baseUrl}/${harvestId}/review`, { approve, note });
  }

  /** Lotes de origem possíveis para a coleta. */
  batches() {
    return this.http.get<BatchOption[]>('/api/v1/production/batches');
  }
}
