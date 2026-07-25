import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { afterEach, beforeEach, describe, expect, it } from 'vitest';
import { AuditApi } from './audit.api';

describe('AuditApi', () => {
  let api: AuditApi;
  let http: HttpTestingController;

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [AuditApi, provideHttpClient(), provideHttpClientTesting()],
    });
    api = TestBed.inject(AuditApi);
    http = TestBed.inject(HttpTestingController);
  });

  afterEach(() => http.verify());

  it('lê os eventos de auditoria', () => {
    let events: unknown;
    api.list().subscribe(e => (events = e));
    const req = http.expectOne('/api/v1/security/audit-events');
    expect(req.request.method).toBe('GET');
    req.flush([{ occurredAt: 'x', action: 'security.login.success', outcome: 'SUCCESS' }]);
    expect(events).toHaveLength(1);
  });

  it('lê o catálogo de usuários (paginado)', () => {
    let users: unknown;
    api.listUsers().subscribe(u => (users = u));
    const req = http.expectOne(r => r.url === '/api/v1/security/users');
    expect(req.request.params.get('size')).toBe('200');
    req.flush({ content: [{ id: 'u1', displayName: 'Ana' }] });
    expect(users).toEqual([{ id: 'u1', displayName: 'Ana' }]);
  });
});
