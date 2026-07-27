import { HttpClient } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Batch } from '../domain/batch.model';

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
}
