import { HttpClient, HttpParams } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';
import { NodeType } from '../domain/genealogy.model';
import { Quarantine, QuarantineDetail } from '../domain/quarantine.model';

@Injectable({ providedIn: 'root' })
export class QuarantinesApi {
  private readonly http = inject(HttpClient);
  private readonly baseUrl = '/api/v1/traceability/quarantines';

  list(onlyOpen: boolean): Observable<Quarantine[]> {
    return this.http.get<Quarantine[]>(this.baseUrl, {
      params: new HttpParams().set('onlyOpen', onlyOpen),
    });
  }

  detail(id: string): Observable<QuarantineDetail> {
    return this.http.get<QuarantineDetail>(`${this.baseUrl}/${id}`);
  }

  open(nodeType: NodeType, nodeId: string, reason: string): Observable<Quarantine> {
    return this.http.post<Quarantine>(this.baseUrl, { nodeType, nodeId, reason });
  }

  release(id: string, justification: string): Observable<void> {
    return this.http.post<void>(`${this.baseUrl}/${id}/release`, { justification });
  }
}
