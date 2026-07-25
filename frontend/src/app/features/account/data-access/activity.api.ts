import { HttpClient } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { LoginEvent, UserSession } from '../domain/activity.model';

/** Autoatendimento de atividade da conta: sessões ativas e histórico de login. */
@Injectable({ providedIn: 'root' })
export class ActivityApi {
  private readonly http = inject(HttpClient);
  private readonly baseUrl = '/api/v1/security';

  listSessions() {
    return this.http.get<UserSession[]>(`${this.baseUrl}/sessions`);
  }

  revokeSession(ref: string) {
    return this.http.delete<void>(`${this.baseUrl}/sessions/${encodeURIComponent(ref)}`);
  }

  /** Encerra todas as sessões, exceto a atual. */
  revokeOtherSessions() {
    return this.http.delete<void>(`${this.baseUrl}/sessions`);
  }

  loginHistory() {
    return this.http.get<LoginEvent[]>(`${this.baseUrl}/login-events`);
  }
}
