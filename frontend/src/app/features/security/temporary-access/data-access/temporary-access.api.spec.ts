import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { afterEach, beforeEach, describe, expect, it } from 'vitest';
import { TemporaryAccessApi } from './temporary-access.api';

describe('TemporaryAccessApi', () => {
  let api: TemporaryAccessApi;
  let http: HttpTestingController;

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [TemporaryAccessApi, provideHttpClient(), provideHttpClientTesting()],
    });
    api = TestBed.inject(TemporaryAccessApi);
    http = TestBed.inject(HttpTestingController);
  });

  afterEach(() => http.verify());

  it('lista concessões', () => {
    let grants: unknown;
    api.list().subscribe(g => (grants = g));
    const req = http.expectOne('/api/v1/security/temporary-access');
    expect(req.request.method).toBe('GET');
    req.flush([{ id: '1', status: 'ACTIVE' }]);
    expect(grants).toHaveLength(1);
  });

  it('solicita, aprova e revoga', () => {
    api.request({ userId: 'u1', permissionCode: 'recipe.read', reason: 'x', durationHours: 8 }).subscribe();
    const post = http.expectOne(r => r.method === 'POST' && r.url === '/api/v1/security/temporary-access');
    expect(post.request.body).toEqual({ userId: 'u1', permissionCode: 'recipe.read', reason: 'x', durationHours: 8 });
    post.flush({ id: 'g1' });

    api.approve('g1').subscribe();
    http.expectOne(r => r.method === 'POST' && r.url === '/api/v1/security/temporary-access/g1/approve').flush(null);

    api.revoke('g1').subscribe();
    http.expectOne(r => r.method === 'DELETE' && r.url === '/api/v1/security/temporary-access/g1').flush(null);
  });

  it('carrega catálogo de usuários (paginado) e permissões', () => {
    let users: unknown;
    api.listUsers().subscribe(u => (users = u));
    const usersReq = http.expectOne(r => r.url === '/api/v1/security/users');
    expect(usersReq.request.params.get('size')).toBe('200');
    usersReq.flush({ content: [{ id: 'u1', displayName: 'Ana' }] });
    expect(users).toEqual([{ id: 'u1', displayName: 'Ana' }]);

    api.listPermissions().subscribe();
    http.expectOne('/api/v1/security/permissions').flush([{ code: 'recipe.read', name: 'Ler receita', critical: false }]);
  });
});
