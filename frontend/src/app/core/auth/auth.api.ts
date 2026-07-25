import { HttpClient } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { LoginRequest, LoginResult, MfaLoginRequest, SessionUser } from './session-user.model';

@Injectable({ providedIn: 'root' })
export class AuthApi {
  private readonly http = inject(HttpClient);
  private readonly baseUrl = '/api/v1/security';

  /** Emite o cookie XSRF-TOKEN, que o HttpClient reenvia no header X-XSRF-TOKEN. */
  csrf() {
    return this.http.get<void>(`${this.baseUrl}/csrf`);
  }

  login(request: LoginRequest) {
    return this.http.post<LoginResult>(`${this.baseUrl}/login`, request);
  }

  /** Conclui o login em duas etapas com o código do segundo fator. */
  completeMfa(request: MfaLoginRequest) {
    return this.http.post<SessionUser>(`${this.baseUrl}/login/mfa`, request);
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
