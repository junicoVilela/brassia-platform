import { HttpClient } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';

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
    return this.http.get<BatchOption[]>('/api/v1/production/batches');
  }
}
