import { HttpClient } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import {
  CreateGroupRequest,
  GroupSummary,
  PermissionSummary,
  UpdateGroupRequest,
} from '../domain/group.model';

@Injectable({ providedIn: 'root' })
export class GroupsApi {
  private readonly http = inject(HttpClient);
  private readonly baseUrl = '/api/v1/security';

  listGroups() {
    return this.http.get<GroupSummary[]>(`${this.baseUrl}/groups`);
  }

  listPermissions() {
    return this.http.get<PermissionSummary[]>(`${this.baseUrl}/permissions`);
  }

  create(request: CreateGroupRequest) {
    return this.http.post<GroupSummary>(`${this.baseUrl}/groups`, request);
  }

  update(groupId: string, request: UpdateGroupRequest) {
    return this.http.patch<GroupSummary>(`${this.baseUrl}/groups/${groupId}`, request);
  }
}
