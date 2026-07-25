import { HttpClient } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { RecoveryCodes, TotpEnrollment } from '../domain/mfa.model';

@Injectable({ providedIn: 'root' })
export class MfaApi {
  private readonly http = inject(HttpClient);
  private readonly baseUrl = '/api/v1/security';

  /** Inicia o enroll: gera segredo e URI otpauth (ainda não ativo até confirmar). */
  enroll() {
    return this.http.post<TotpEnrollment>(`${this.baseUrl}/totp/enroll`, {});
  }

  confirm(code: string) {
    return this.http.post<void>(`${this.baseUrl}/totp/confirm`, { code });
  }

  disable(currentPassword?: string) {
    const body = currentPassword ? { currentPassword } : {};
    return this.http.request<void>('DELETE', `${this.baseUrl}/totp`, { body });
  }

  regenerateRecoveryCodes() {
    return this.http.post<RecoveryCodes>(`${this.baseUrl}/recovery-codes/regenerate`, {});
  }
}
