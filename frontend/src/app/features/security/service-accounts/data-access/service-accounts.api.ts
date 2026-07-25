import { HttpClient } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { CreateServiceAccount, ServiceAccount } from '../domain/service-account.model';

interface IssueResponse {
  credentialId: string;
  rawKey: string;
  keyPrefix: string;
}

@Injectable({ providedIn: 'root' })
export class ServiceAccountsApi {
  private readonly http = inject(HttpClient);
  private readonly baseUrl = '/api/v1/security/service-accounts';

  list() {
    return this.http.get<ServiceAccount[]>(this.baseUrl);
  }

  create(body: CreateServiceAccount) {
    return this.http.post<ServiceAccount>(this.baseUrl, body);
  }

  issueCredential(serviceAccountId: string, scopes: string[]) {
    return this.http.post<IssueResponse>(`${this.baseUrl}/${serviceAccountId}/credentials`, { scopes });
  }

  revokeCredential(credentialId: string) {
    return this.http.post<void>(`${this.baseUrl}/credentials/${credentialId}/revoke`, {});
  }
}
