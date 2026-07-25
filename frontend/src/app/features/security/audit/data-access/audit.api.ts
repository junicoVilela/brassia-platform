import { HttpClient } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { map } from 'rxjs';
import { AuditEvent } from '../domain/audit-event.model';

@Injectable({ providedIn: 'root' })
export class AuditApi {
  private readonly http = inject(HttpClient);
  private readonly baseUrl = '/api/v1/security';

  /** 50 eventos mais recentes da cervejaria ativa (mais novos primeiro). */
  list() {
    return this.http.get<AuditEvent[]>(`${this.baseUrl}/audit-events`);
  }

  /** Catálogo de usuários para resolver o nome do ator (opcional). */
  listUsers() {
    return this.http
      .get<{ content: { id: string; displayName: string }[] }>(`${this.baseUrl}/users`, {
        params: { page: 0, size: 200 },
      })
      .pipe(map(page => page.content));
  }
}
