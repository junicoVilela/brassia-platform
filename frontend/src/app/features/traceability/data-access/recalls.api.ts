import { HttpClient } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';
import { NodeType } from '../domain/genealogy.model';
import { Recall, RecallDossier } from '../domain/recall.model';

@Injectable({ providedIn: 'root' })
export class RecallsApi {
  private readonly http = inject(HttpClient);
  private readonly baseUrl = '/api/v1/traceability/recalls';

  list(): Observable<Recall[]> {
    return this.http.get<Recall[]>(this.baseUrl);
  }

  dossier(id: string): Observable<RecallDossier> {
    return this.http.get<RecallDossier>(`${this.baseUrl}/${id}`);
  }

  open(nodeType: NodeType, nodeId: string, reason: string): Observable<Recall> {
    return this.http.post<Recall>(this.baseUrl, { nodeType, nodeId, reason });
  }

  notify(id: string, notificationId: string, channel: string, note: string | null): Observable<void> {
    return this.http.post<void>(`${this.baseUrl}/${id}/notifications/${notificationId}`, {
      channel,
      note,
    });
  }

  close(id: string, summary: string): Observable<void> {
    return this.http.post<void>(`${this.baseUrl}/${id}/close`, { summary });
  }
}
