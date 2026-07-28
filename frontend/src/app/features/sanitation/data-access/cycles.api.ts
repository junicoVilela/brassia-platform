import { HttpClient } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { CleaningCycle, RecordStepRequest, StartCycleRequest } from '../domain/cycle.model';

@Injectable({ providedIn: 'root' })
export class CyclesApi {
  private readonly http = inject(HttpClient);
  private readonly baseUrl = '/api/v1/sanitation/cycles';

  list() {
    return this.http.get<CleaningCycle[]>(this.baseUrl);
  }

  get(id: string) {
    return this.http.get<CleaningCycle>(`${this.baseUrl}/${id}`);
  }

  start(request: StartCycleRequest) {
    return this.http.post<{ id: string }>(this.baseUrl, request);
  }

  recordStep(id: string, request: RecordStepRequest) {
    return this.http.post<void>(`${this.baseUrl}/${id}/steps`, request);
  }

  interrupt(id: string, reason: string) {
    return this.http.post<void>(`${this.baseUrl}/${id}/interrupt`, { reason });
  }

  resume(id: string) {
    return this.http.post<void>(`${this.baseUrl}/${id}/resume`, {});
  }

  complete(id: string) {
    return this.http.post<void>(`${this.baseUrl}/${id}/complete`, {});
  }
}
