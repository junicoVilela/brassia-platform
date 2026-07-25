import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { afterEach, beforeEach, describe, expect, it } from 'vitest';
import { FederationApi } from './federation.api';

describe('FederationApi', () => {
  let api: FederationApi;
  let http: HttpTestingController;

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [FederationApi, provideHttpClient(), provideHttpClientTesting()],
    });
    api = TestBed.inject(FederationApi);
    http = TestBed.inject(HttpTestingController);
  });

  afterEach(() => http.verify());

  it('lista provedores', () => {
    api.list().subscribe();
    const req = http.expectOne('/api/v1/security/federation-providers');
    expect(req.request.method).toBe('GET');
    req.flush([]);
  });

  it('cria provedor com configuração', () => {
    api.create({
      code: 'okta', displayName: 'Okta', protocol: 'OIDC',
      issuerOrEntityId: 'https://idp', configuration: { metadataUri: 'https://idp/meta' },
    }).subscribe();
    const req = http.expectOne(r => r.method === 'POST' && r.url === '/api/v1/security/federation-providers');
    expect(req.request.body.protocol).toBe('OIDC');
    expect(req.request.body.configuration).toEqual({ metadataUri: 'https://idp/meta' });
    req.flush({ id: 'p1' });
  });

  it('valida a metadata do provedor', () => {
    api.validate('p1').subscribe();
    const req = http.expectOne('/api/v1/security/federation-providers/p1/validate');
    expect(req.request.method).toBe('POST');
    req.flush(null);
  });

  it('lista as identidades vinculadas do provedor', () => {
    let identities: unknown;
    api.listIdentities('p1').subscribe(i => (identities = i));
    const req = http.expectOne('/api/v1/security/federation-providers/p1/identities');
    expect(req.request.method).toBe('GET');
    req.flush([{ userId: 'u1', externalSubject: 'okta|1', normalizedEmail: 'a@x.com', linkedAt: 'x' }]);
    expect(identities).toHaveLength(1);
  });

  it('gerencia mapeamentos SCIM (listar/upsert/desativar)', () => {
    api.listScimMappings('p1').subscribe();
    http.expectOne(r => r.method === 'GET' && r.url === '/api/v1/security/federation-providers/p1/scim-mappings')
      .flush([{ externalGroupId: 'idp-admins', securityGroupId: 'g1', active: true }]);

    api.upsertScimMapping('p1', 'idp-admins', 'g1').subscribe();
    const post = http.expectOne(r => r.method === 'POST' && r.url === '/api/v1/security/federation-providers/p1/scim-mappings');
    expect(post.request.body).toEqual({ externalGroupId: 'idp-admins', securityGroupId: 'g1' });
    post.flush(null);

    api.deactivateScimMapping('p1', 'idp/admins').subscribe();
    http.expectOne(r => r.method === 'DELETE'
      && r.url === '/api/v1/security/federation-providers/p1/scim-mappings/idp%2Fadmins').flush(null);
  });

  it('normaliza o catálogo de grupos', () => {
    let groups: unknown;
    api.listGroups().subscribe(g => (groups = g));
    http.expectOne('/api/v1/security/groups').flush([{ id: 'g1', name: 'Admin', code: 'ADMIN' }]);
    expect(groups).toEqual([{ id: 'g1', name: 'Admin' }]);
  });
});
