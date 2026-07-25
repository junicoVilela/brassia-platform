import { HttpClient } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { map } from 'rxjs';
import {
  PermissionOption,
  RequestGrant,
  TemporaryGrant,
  UserOption,
} from '../domain/temporary-access.model';

interface PageResponse<T> {
  content: T[];
}

@Injectable({ providedIn: 'root' })
export class TemporaryAccessApi {
  private readonly http = inject(HttpClient);
  private readonly baseUrl = '/api/v1/security/temporary-access';

  list() {
    return this.http.get<TemporaryGrant[]>(this.baseUrl);
  }

  request(body: RequestGrant) {
    return this.http.post<{ id: string }>(this.baseUrl, body);
  }

  approve(id: string) {
    return this.http.post<void>(`${this.baseUrl}/${id}/approve`, {});
  }

  revoke(id: string) {
    return this.http.delete<void>(`${this.baseUrl}/${id}`);
  }

  /** Catálogos auxiliares para os selects (podem falhar por permissão; tratados como opcionais). */
  listUsers() {
    return this.http
      .get<PageResponse<UserOption>>('/api/v1/security/users', { params: { page: 0, size: 200 } })
      .pipe(map(page => page.content));
  }

  listPermissions() {
    return this.http.get<PermissionOption[]>('/api/v1/security/permissions');
  }
}
