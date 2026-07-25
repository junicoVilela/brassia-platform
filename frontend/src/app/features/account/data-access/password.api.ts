import { HttpClient } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';

@Injectable({ providedIn: 'root' })
export class PasswordApi {
  private readonly http = inject(HttpClient);
  private readonly baseUrl = '/api/v1/security';

  /** Troca a senha do usuário autenticado (exige a senha atual). */
  change(currentPassword: string, newPassword: string) {
    return this.http.post<void>(`${this.baseUrl}/password/change`, { currentPassword, newPassword });
  }
}
