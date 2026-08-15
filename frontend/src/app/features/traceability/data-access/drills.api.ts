import { HttpClient } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';
import { NodeType } from '../domain/genealogy.model';
import { DrillCapaAction, DrillReport, RecallDrill } from '../domain/drill.model';

@Injectable({ providedIn: 'root' })
export class DrillsApi {
  private readonly http = inject(HttpClient);
  private readonly baseUrl = '/api/v1/traceability/recall-drills';

  list(): Observable<RecallDrill[]> {
    return this.http.get<RecallDrill[]>(this.baseUrl);
  }

  report(id: string): Observable<DrillReport> {
    return this.http.get<DrillReport>(`${this.baseUrl}/${id}`);
  }

  start(nodeType: NodeType, nodeId: string, note: string | null): Observable<RecallDrill> {
    return this.http.post<RecallDrill>(this.baseUrl, { nodeType, nodeId, note });
  }

  /**
   * Encerra o simulado.
   *
   * <p>Ou texto livre, ou ações de CAPA numa NC — nunca os dois: o servidor recusa, porque quem lê o
   * relatório não saberia qual é a ação de verdade.
   */
  finish(
    id: string,
    unitsLocated: number,
    summary: string,
    correctiveActions: string | null,
    nonConformityId: string | null = null,
    capaActions: DrillCapaAction[] = [],
  ): Observable<void> {
    return this.http.post<void>(`${this.baseUrl}/${id}/finish`, {
      unitsLocated,
      summary,
      correctiveActions: nonConformityId ? null : correctiveActions,
      nonConformityId,
      capaActions,
    });
  }
}
