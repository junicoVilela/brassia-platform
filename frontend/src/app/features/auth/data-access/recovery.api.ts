import { HttpClient } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { switchMap } from 'rxjs';

/**
 * Fluxos anônimos de recuperação de conta (fora do authGuard). Cada POST é
 * precedido de um GET /csrf para obter o cookie XSRF-TOKEN que o HttpClient
 * reenvia no header X-XSRF-TOKEN.
 */
@Injectable({ providedIn: 'root' })
export class RecoveryApi {
  private readonly http = inject(HttpClient);
  private readonly baseUrl = '/api/v1/security';

  private csrf() {
    return this.http.get<void>(`${this.baseUrl}/csrf`);
  }

  /** Resposta sempre neutra: não revela se o e-mail existe. */
  forgotPassword(email: string) {
    return this.csrf().pipe(switchMap(() => this.http.post<void>(`${this.baseUrl}/password/forgot`, { email })));
  }

  resetPassword(token: string, newPassword: string) {
    return this.csrf().pipe(
      switchMap(() => this.http.post<void>(`${this.baseUrl}/password/reset`, { token, newPassword })),
    );
  }

  confirmEmailVerification(token: string) {
    return this.csrf().pipe(
      switchMap(() => this.http.post<void>(`${this.baseUrl}/email-verification/confirm`, { token })),
    );
  }
}
