import { HttpClient } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { map } from 'rxjs';
import {
  CreateFederationProvider,
  ExternalIdentity,
  FederationProvider,
  GroupOption,
  ScimMapping,
} from '../domain/federation.model';

@Injectable({ providedIn: 'root' })
export class FederationApi {
  private readonly http = inject(HttpClient);
  private readonly baseUrl = '/api/v1/security/federation-providers';

  list() {
    return this.http.get<FederationProvider[]>(this.baseUrl);
  }

  create(body: CreateFederationProvider) {
    return this.http.post<{ id: string }>(this.baseUrl, body);
  }

  /** Valida a metadata/config do provedor. */
  validate(id: string) {
    return this.http.post<void>(`${this.baseUrl}/${id}/validate`, {});
  }

  /** Identidades externas vinculadas a um provedor. */
  listIdentities(id: string) {
    return this.http.get<ExternalIdentity[]>(`${this.baseUrl}/${id}/identities`);
  }

  listScimMappings(id: string) {
    return this.http.get<ScimMapping[]>(`${this.baseUrl}/${id}/scim-mappings`);
  }

  upsertScimMapping(id: string, externalGroupId: string, securityGroupId: string) {
    return this.http.post<void>(`${this.baseUrl}/${id}/scim-mappings`, { externalGroupId, securityGroupId });
  }

  deactivateScimMapping(id: string, externalGroupId: string) {
    return this.http.delete<void>(`${this.baseUrl}/${id}/scim-mappings/${encodeURIComponent(externalGroupId)}`);
  }

  /** Catálogo de grupos internos, normalizado para o seletor. */
  listGroups() {
    return this.http
      .get<{ id: string; name: string }[]>('/api/v1/security/groups')
      .pipe(map(groups => groups.map((g): GroupOption => ({ id: g.id, name: g.name }))));
  }
}
