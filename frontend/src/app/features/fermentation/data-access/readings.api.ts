import { HttpClient, HttpParams } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { map } from 'rxjs';
import { MAX_PAGE_SIZE, PageResponse } from '../../../core/http/page.model';
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
    // A listagem é paginada (REL-002). Estes clientes só preenchem seletor: pedem o teto de uma
    // página e mapeiam `content`. O que NÃO se faz aqui é ignorar o truncamento — quem consome
    // decide o que mostrar, mas o dado de que há mais vem junto.
    return this.http
      .get<PageResponse<BatchOption>>('/api/v1/production/batches', {
        params: new HttpParams().set('page', 0).set('size', MAX_PAGE_SIZE),
      })
      .pipe(map(page => page.content));
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

  /** O critério vem do perfil da agenda do lote (FER-004); sem agenda, o backend recusa. */
  fgStability(batchId: string) {
    return this.http.get<FgStability>(`/api/v1/fermentation/batches/${batchId}/fg-stability`);
  }

  record(request: RecordReadingRequest) {
    return this.http.post<{ id: string; valid: boolean; invalidReason: string }>(this.baseUrl, request);
  }
}
