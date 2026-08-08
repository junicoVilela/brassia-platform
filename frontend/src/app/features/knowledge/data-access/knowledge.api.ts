import { HttpClient } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';
import { Evidence, IndexRequest, KnowledgeDocument } from '../domain/knowledge.model';

@Injectable({ providedIn: 'root' })
export class KnowledgeApi {
  private readonly http = inject(HttpClient);
  private readonly baseUrl = '/api/v1/knowledge';

  documents(): Observable<KnowledgeDocument[]> {
    return this.http.get<KnowledgeDocument[]>(`${this.baseUrl}/documents`);
  }

  index(request: IndexRequest): Observable<KnowledgeDocument> {
    return this.http.post<KnowledgeDocument>(`${this.baseUrl}/documents`, request);
  }

  /**
   * `onDate` é o que permite perguntar sobre o passado: "o que a FISPQ dizia quando o lote foi
   * produzido?" é outra pergunta que "o que ela diz hoje".
   */
  search(question: string, onDate: string | null, equipmentId: string | null): Observable<Evidence[]> {
    const params: Record<string, string> = { question };
    if (onDate) {
      params['onDate'] = onDate;
    }
    if (equipmentId) {
      params['equipmentId'] = equipmentId;
    }
    return this.http.get<Evidence[]>(`${this.baseUrl}/search`, { params });
  }
}
