import { HttpClient } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { CreateFederationProvider, ExternalIdentity, FederationProvider } from '../domain/federation.model';

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
}
