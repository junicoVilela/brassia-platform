import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { afterEach, beforeEach, describe, expect, it } from 'vitest';
import { AuditApi } from './audit.api';
import { AuditFilter } from '../domain/audit-event.model';

const emptyFilter: AuditFilter = { action: '', targetType: '', outcome: '', actorId: '', from: '', to: '' };

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

  it('busca com paginação e sem filtros vazios', () => {
    api.search(emptyFilter, 2, 25).subscribe();
    const req = http.expectOne(r => r.url === '/api/v1/security/audit-events');
    expect(req.request.params.get('page')).toBe('2');
    expect(req.request.params.get('size')).toBe('25');
    expect(req.request.params.has('action')).toBe(false);
    expect(req.request.params.has('from')).toBe(false);
    req.flush({ content: [], page: 2, size: 25, totalElements: 0, totalPages: 0 });
  });

  it('envia filtros preenchidos e datas em ISO', () => {
    api.search({ ...emptyFilter, action: 'login', outcome: 'SUCCESS', from: '2026-07-05T00:00' }, 0, 25).subscribe();
    const req = http.expectOne(r => r.url === '/api/v1/security/audit-events');
    expect(req.request.params.get('action')).toBe('login');
    expect(req.request.params.get('outcome')).toBe('SUCCESS');
    expect(req.request.params.get('from')).toContain('T');
    req.flush({ content: [], page: 0, size: 25, totalElements: 0, totalPages: 0 });
  });

  it('lê o catálogo de usuários', () => {
    api.listUsers().subscribe();
    const req = http.expectOne(r => r.url === '/api/v1/security/users');
    expect(req.request.params.get('size')).toBe('200');
    req.flush({ content: [{ id: 'u1', displayName: 'Ana' }] });
  });
});
