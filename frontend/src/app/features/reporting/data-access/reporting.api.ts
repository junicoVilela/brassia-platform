import { HttpClient } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';
import { BatchReport } from '../domain/batch-report.model';

/** Opção de lote para a tela; o backend publica mais campos, usamos só estes. */
export interface BatchOption {
  id: string;
  code: string;
  recipeName: string;
  status: string;
}

@Injectable({ providedIn: 'root' })
export class ReportingApi {
  private readonly http = inject(HttpClient);
  private readonly baseUrl = '/api/v1/reporting';

  ofBatch(batchId: string): Observable<BatchReport> {
    return this.http.get<BatchReport>(`${this.baseUrl}/batches/${batchId}`);
  }

  /** POST porque exportar não é só ler: o documento sai do sistema e a saída fica auditada. */
  export(batchId: string): Observable<BatchReport> {
    return this.http.post<BatchReport>(`${this.baseUrl}/batches/${batchId}/export`, {});
  }

  batches(): Observable<BatchOption[]> {
    return this.http.get<BatchOption[]>('/api/v1/production/batches');
  }
}
