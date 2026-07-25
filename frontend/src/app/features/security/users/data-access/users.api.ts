import { HttpClient } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { map } from 'rxjs';
import { GroupOption, InviteUserRequest, SecurityUserSummary } from '../domain/user.model';

interface GroupCatalogItem {
  id: string;
  code: string;
  name: string;
}

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

  listMemberships(userId: string) {
    return this.http.get<GroupOption[]>(`${this.baseUrl}/${userId}/memberships`);
  }

  grantMembership(userId: string, groupId: string) {
    return this.http.post<void>(`${this.baseUrl}/${userId}/memberships`, { groupId });
  }

  revokeMembership(userId: string, groupId: string) {
    return this.http.delete<void>(`${this.baseUrl}/${userId}/memberships/${groupId}`);
  }

  /** Catálogo de grupos da cervejaria, normalizado para a forma mínima usada na tela. */
  listGroups() {
    return this.http.get<GroupCatalogItem[]>('/api/v1/security/groups').pipe(
      map(groups => groups.map((g): GroupOption => ({ groupId: g.id, code: g.code, name: g.name }))),
    );
  }
}
