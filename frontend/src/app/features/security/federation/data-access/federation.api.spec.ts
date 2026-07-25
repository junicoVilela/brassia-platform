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
});
