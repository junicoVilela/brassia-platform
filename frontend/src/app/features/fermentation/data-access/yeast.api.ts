import { HttpClient, HttpParams } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { map } from 'rxjs';
import { MAX_PAGE_SIZE, PageResponse } from '../../../core/http/page.model';
import { BatchOption } from '../domain/reading.model';
import { CollectYeastRequest, YeastHarvest, YeastPolicy, YeastReuse } from '../domain/yeast.model';

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

  /** Recomendação de repitch; strainId nulo considera todas as cepas. */
  reuse(strainId: string | null) {
    let params = new HttpParams();
    if (strainId) {
      params = params.set('strainId', strainId);
    }
    return this.http.get<YeastReuse>('/api/v1/fermentation/yeast/reuse', { params });
  }

  policy() {
    return this.http.get<YeastPolicy>('/api/v1/fermentation/yeast/policy');
  }

  savePolicy(policy: YeastPolicy) {
    return this.http.put<void>('/api/v1/fermentation/yeast/policy', policy);
  }

  /** O uso nunca é implícito: confirmed=false é recusado pelo backend. */
  use(harvestId: string, targetBatchId: string) {
    return this.http.post<void>(`${this.baseUrl}/${harvestId}/use`, { targetBatchId, confirmed: true });
  }

  /** Lotes de origem possíveis para a coleta. */
  batches() {
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
