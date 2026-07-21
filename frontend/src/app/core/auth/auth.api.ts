import { HttpClient } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { LoginRequest, SessionUser } from './session-user.model';

@Injectable({ providedIn: 'root' })
export class AuthApi {
  private readonly http = inject(HttpClient);
  private readonly baseUrl = '/api/v1/security';

  /** Emite o cookie XSRF-TOKEN, que o HttpClient reenvia no header X-XSRF-TOKEN. */
  csrf() {
    return this.http.get<void>(`${this.baseUrl}/csrf`);
  }

  login(request: LoginRequest) {
    return this.http.post<SessionUser>(`${this.baseUrl}/login`, request);
  }

  logout() {
    return this.http.post<void>(`${this.baseUrl}/logout`, {});
  }

  session() {
    return this.http.get<SessionUser>(`${this.baseUrl}/session`);
  }

  switchBrewery(breweryId: string) {
    return this.http.post<SessionUser>(`${this.baseUrl}/session/brewery`, { breweryId });
  }
}
