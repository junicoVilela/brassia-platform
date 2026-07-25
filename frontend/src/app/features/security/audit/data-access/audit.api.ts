import { HttpClient } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { map } from 'rxjs';
import { AuditFilter, AuditPage } from '../domain/audit-event.model';

@Injectable({ providedIn: 'root' })
export class AuditApi {
  private readonly http = inject(HttpClient);
  private readonly baseUrl = '/api/v1/security';

  /** Busca paginada com filtros server-side. Datas são enviadas em ISO 8601. */
  search(filter: AuditFilter, page: number, size: number) {
    const params: Record<string, string> = { page: String(page), size: String(size) };
    if (filter.action.trim()) {
      params['action'] = filter.action.trim();
    }
    if (filter.targetType.trim()) {
      params['targetType'] = filter.targetType.trim();
    }
    if (filter.outcome) {
      params['outcome'] = filter.outcome;
    }
    if (filter.actorId) {
      params['actorId'] = filter.actorId;
    }
    if (filter.from) {
      params['from'] = new Date(filter.from).toISOString();
    }
    if (filter.to) {
      params['to'] = new Date(filter.to).toISOString();
    }
    return this.http.get<AuditPage>(`${this.baseUrl}/audit-events`, { params });
  }

  /** Catálogo de usuários para o seletor de ator (opcional). */
  listUsers() {
    return this.http
      .get<{ content: { id: string; displayName: string }[] }>(`${this.baseUrl}/users`, {
        params: { page: 0, size: 200 },
      })
      .pipe(map(response => response.content));
  }
}
