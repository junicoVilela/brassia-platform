import { HttpClient, HttpParams } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { MAX_PAGE_SIZE, PageResponse } from '../../../core/http/page.model';
import { Observable, map } from 'rxjs';

/** Opção de lote para a tela; o backend publica mais campos, usamos só estes. */
export interface BatchOption {
  id: string;
  code: string;
  recipeName: string;
  status: string;
}

/**
 * Lotes, para escolher o que avaliar (AIA-002).
 *
 * Cliente separado do `AiApi` porque a rota é da produção, não da IA — e misturar as duas faria parecer
 * que o módulo de IA é dono da lista de lotes.
 */
@Injectable({ providedIn: 'root' })
export class BatchesApi {
  private readonly http = inject(HttpClient);

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
