import { HttpClient } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';
import { BatchCost } from '../domain/batch-cost.model';
import { BatchVariance } from '../domain/batch-variance.model';

/** Opção de lote para a tela; o backend publica mais campos, usamos só estes. */
export interface BatchOption {
  id: string;
  code: string;
  recipeName: string;
  status: string;
}

@Injectable({ providedIn: 'root' })
export class CostingApi {
  private readonly http = inject(HttpClient);
  private readonly baseUrl = '/api/v1/costing';

  closed(): Observable<BatchCost[]> {
    return this.http.get<BatchCost[]>(`${this.baseUrl}/batch-costs`);
  }

  ofBatch(batchId: string): Observable<BatchCost> {
    return this.http.get<BatchCost>(`${this.baseUrl}/batches/${batchId}`);
  }

  close(batchId: string, note: string | null): Observable<BatchCost> {
    return this.http.post<BatchCost>(`${this.baseUrl}/batches/${batchId}/close`, { note });
  }

  batches(): Observable<BatchOption[]> {
    return this.http.get<BatchOption[]>('/api/v1/production/batches');
  }

  /** Planejado versus real (CST-002); alçada própria, separada da leitura do custo. */
  variance(batchId: string): Observable<BatchVariance> {
    return this.http.get<BatchVariance>(`${this.baseUrl}/batches/${batchId}/variance`);
  }
}
