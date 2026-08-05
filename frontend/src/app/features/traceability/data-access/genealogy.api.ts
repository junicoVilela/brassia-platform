import { HttpClient, HttpParams } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';
import { Genealogy, GenealogyQuery } from '../domain/genealogy.model';

@Injectable({ providedIn: 'root' })
export class GenealogyApi {
  private readonly http = inject(HttpClient);

  genealogy(query: GenealogyQuery): Observable<Genealogy> {
    const params = new HttpParams()
      .set('nodeType', query.nodeType)
      .set('nodeId', query.nodeId)
      .set('direction', query.direction)
      .set('depth', query.depth);
    return this.http.get<Genealogy>('/api/v1/traceability/genealogy', { params });
  }
}
