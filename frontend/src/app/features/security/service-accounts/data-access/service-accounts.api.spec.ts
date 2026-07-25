import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { afterEach, beforeEach, describe, expect, it } from 'vitest';
import { ServiceAccountsApi } from './service-accounts.api';

describe('ServiceAccountsApi', () => {
  let api: ServiceAccountsApi;
  let http: HttpTestingController;

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [ServiceAccountsApi, provideHttpClient(), provideHttpClientTesting()],
    });
    api = TestBed.inject(ServiceAccountsApi);
    http = TestBed.inject(HttpTestingController);
  });

  afterEach(() => http.verify());

  it('lista e cria contas de serviço', () => {
    api.list().subscribe();
    http.expectOne(r => r.method === 'GET' && r.url === '/api/v1/security/service-accounts').flush([]);

    api.create({ code: 'ci', name: 'CI' }).subscribe();
    const post = http.expectOne(r => r.method === 'POST' && r.url === '/api/v1/security/service-accounts');
    expect(post.request.body).toEqual({ code: 'ci', name: 'CI' });
    post.flush({ id: 's1', code: 'ci', active: true });
  });

  it('emite credencial com escopos e revoga', () => {
    let issued: unknown;
    api.issueCredential('s1', ['scim.read']).subscribe(r => (issued = r));
    const issue = http.expectOne('/api/v1/security/service-accounts/s1/credentials');
    expect(issue.request.body).toEqual({ scopes: ['scim.read'] });
    issue.flush({ credentialId: 'c1', rawKey: 'brassia_abc', keyPrefix: 'brassia_a' });
    expect(issued).toEqual({ credentialId: 'c1', rawKey: 'brassia_abc', keyPrefix: 'brassia_a' });

    api.revokeCredential('c1').subscribe();
    const revoke = http.expectOne('/api/v1/security/service-accounts/credentials/c1/revoke');
    expect(revoke.request.method).toBe('POST');
    revoke.flush(null);
  });
});
