import { HttpClient, HttpParams } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable, map } from 'rxjs';
import { MAX_PAGE_SIZE, PageResponse } from '../../../core/http/page.model';
import { BatchAlert, CreateAlertRequest } from '../domain/alert.model';
import { Batch } from '../domain/batch.model';
import {
  ApplyCorrectionRequest,
  AppliedCorrection,
  BrewCorrection,
  CorrectionResult,
  PreviewCorrectionRequest,
} from '../domain/correction.model';
import { Measurement, RecordMeasurementRequest } from '../domain/measurement.model';
import { Transfer, TransferRequest } from '../domain/transfer.model';

@Injectable({ providedIn: 'root' })
export class BatchesApi {
  private readonly http = inject(HttpClient);
  private readonly baseUrl = '/api/v1/production/batches';

  /**
   * Uma página de lotes (REL-002).
   *
   * A listagem deixou de devolver tudo: crescia com o histórico e cruzaria a meta de 500 ms por volta de
   * 4.700 lotes. O envelope traz `totalElements`, que é o que permite a quem consome saber que existe
   * mais do que veio.
   */
  list(page = 0, size = 20) {
    const params = new HttpParams().set('page', page).set('size', size);
    return this.http.get<PageResponse<Batch>>(this.baseUrl, { params });
  }

  /**
   * Os lotes para preencher um seletor, com o aviso de truncamento junto.
   *
   * Existe para que os cinco lugares que só querem popular um `<select>` não repitam a mesma decisão — e,
   * principalmente, para que nenhum deles trunque em silêncio. Um seletor que mostra 100 de 3.000 lotes
   * sem dizer nada é pior que um seletor vazio: o lote procurado simplesmente não está lá, e quem procura
   * conclui que ele não existe.
   */
  listForSelection(): Observable<{ items: Batch[]; truncated: boolean; total: number }> {
    return this.list(0, MAX_PAGE_SIZE).pipe(
      map(page => ({
        items: page.content,
        truncated: page.totalElements > page.content.length,
        total: page.totalElements,
      })),
    );
  }

  get(batchId: string) {
    return this.http.get<Batch>(`${this.baseUrl}/${batchId}`);
  }

  completeStep(batchId: string, stepId: string) {
    return this.http.post<Batch>(`${this.baseUrl}/${batchId}/steps/${stepId}/complete`, {});
  }

  measurements(batchId: string) {
    return this.http.get<Measurement[]>(`${this.baseUrl}/${batchId}/measurements`);
  }

  recordMeasurement(batchId: string, request: RecordMeasurementRequest) {
    return this.http.post<{ id: string }>(`${this.baseUrl}/${batchId}/measurements`, request);
  }

  corrections() {
    return this.http.get<BrewCorrection[]>('/api/v1/production/corrections');
  }

  previewCorrection(batchId: string, request: PreviewCorrectionRequest) {
    return this.http.post<CorrectionResult>(`${this.baseUrl}/${batchId}/corrections/preview`, request);
  }

  appliedCorrections(batchId: string) {
    return this.http.get<AppliedCorrection[]>(`${this.baseUrl}/${batchId}/corrections/applied`);
  }

  applyCorrection(batchId: string, request: ApplyCorrectionRequest) {
    return this.http.post<AppliedCorrection>(`${this.baseUrl}/${batchId}/corrections/apply`, request);
  }

  transfer(batchId: string, request: TransferRequest) {
    return this.http.post<Transfer>(`${this.baseUrl}/${batchId}/transfer`, request);
  }

  alerts(batchId: string) {
    return this.http.get<BatchAlert[]>(`${this.baseUrl}/${batchId}/alerts`);
  }

  createAlert(batchId: string, request: CreateAlertRequest) {
    return this.http.post<{ id: string }>(`${this.baseUrl}/${batchId}/alerts`, request);
  }

  confirmAlert(batchId: string, alertId: string) {
    return this.http.post<BatchAlert>(`${this.baseUrl}/${batchId}/alerts/${alertId}/confirm`, {});
  }
}
