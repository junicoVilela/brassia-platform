import { HttpClient } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { InviteUserRequest, SecurityUserSummary } from '../domain/user.model';

interface PageResponse<T> {
  content: T[];
  page: number;
  size: number;
  totalElements: number;
  totalPages: number;
}

@Injectable({ providedIn: 'root' })
export class UsersApi {
  private readonly http = inject(HttpClient);
  private readonly baseUrl = '/api/v1/security/users';

  list(page = 0, size = 20) {
    return this.http.get<PageResponse<SecurityUserSummary>>(this.baseUrl, {
      params: { page, size },
    });
  }

  invite(request: InviteUserRequest) {
    return this.http.post<{ userId: string; email: string; status: string }>(this.baseUrl, request);
  }

  block(userId: string) {
    return this.http.post<void>(`${this.baseUrl}/${userId}/block`, {});
  }

  unblock(userId: string) {
    return this.http.post<void>(`${this.baseUrl}/${userId}/unblock`, {});
  }

  disable(userId: string) {
    return this.http.post<void>(`${this.baseUrl}/${userId}/disable`, {});
  }
}
