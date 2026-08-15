import { HttpClient, HttpParams } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { MAX_PAGE_SIZE, PageResponse } from '../../../core/http/page.model';
import { Observable, map } from 'rxjs';
import { BatchReport } from '../domain/batch-report.model';
import { Dashboard } from '../domain/dashboard.model';

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

  /**
   * O mesmo documento impresso (RPT-001-A).
   *
   * <p>Mesma rota e mesma alçada — o que muda é o `Accept`. O PDF vem como blob porque é binário:
   * deixá-lo passar pelo parser de JSON o corromperia silenciosamente.
   */
  exportPdf(batchId: string): Observable<Blob> {
    return this.http.post(`${this.baseUrl}/batches/${batchId}/export`, {}, {
      headers: { Accept: 'application/pdf' },
      responseType: 'blob',
    });
  }

  dashboard(from: string, to: string): Observable<Dashboard> {
    const params = new HttpParams().set('from', from).set('to', to);
    return this.http.get<Dashboard>(`${this.baseUrl}/dashboard`, { params });
  }

  batches(): Observable<BatchOption[]> {
    // A listagem é paginada (REL-002). Estes clientes só preenchem seletor: pedem o teto de uma
    // página e mapeiam `content`. O que NÃO se faz aqui é ignorar o truncamento — quem consome
    // decide o que mostrar, mas o dado de que há mais vem junto.
    return this.http
      .get<PageResponse<BatchOption>>('/api/v1/production/batches', {
        params: new HttpParams().set('page', 0).set('size', MAX_PAGE_SIZE),
      })
      .pipe(map(page => page.content));
  }
}
