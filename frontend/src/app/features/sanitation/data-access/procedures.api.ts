import { HttpClient } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { CreateProcedureRequest, Procedure } from '../domain/procedure.model';

@Injectable({ providedIn: 'root' })
export class ProceduresApi {
  private readonly http = inject(HttpClient);
  private readonly baseUrl = '/api/v1/sanitation/procedures';

  list() {
    return this.http.get<Procedure[]>(this.baseUrl);
  }

  create(request: CreateProcedureRequest) {
    return this.http.post<{ id: string; version: number }>(this.baseUrl, request);
  }

  publish(id: string) {
    return this.http.post<void>(`${this.baseUrl}/${id}/publish`, {});
  }
}
