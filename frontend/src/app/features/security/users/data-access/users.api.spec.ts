import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { afterEach, beforeEach, describe, expect, it } from 'vitest';
import { UsersApi } from './users.api';

describe('UsersApi', () => {
  let api: UsersApi;
  let http: HttpTestingController;

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [UsersApi, provideHttpClient(), provideHttpClientTesting()],
    });
    api = TestBed.inject(UsersApi);
    http = TestBed.inject(HttpTestingController);
  });

  afterEach(() => http.verify());

  it('lista usuários no endpoint paginado', () => {
    let content: unknown;
    api.list(0, 20).subscribe(page => (content = page.content));

    const req = http.expectOne(r => r.url === '/api/v1/security/users');
    expect(req.request.method).toBe('GET');
    expect(req.request.params.get('page')).toBe('0');
    expect(req.request.params.get('size')).toBe('20');
    req.flush({ content: [{ id: '1', email: 'a@x.com', displayName: 'Ana', status: 'INVITED', emailVerifiedAt: null }],
               page: 0, size: 20, totalElements: 1, totalPages: 1 });

    expect(content).toHaveLength(1);
  });

  it('convida via POST na coleção', () => {
    api.invite({ email: 'a@x.com', displayName: 'Ana' }).subscribe();
    const req = http.expectOne('/api/v1/security/users');
    expect(req.request.method).toBe('POST');
    expect(req.request.body).toEqual({ email: 'a@x.com', displayName: 'Ana' });
    req.flush({ userId: '1', email: 'a@x.com', status: 'INVITED' });
  });

  it('aplica ações administrativas nos sub-recursos', () => {
    api.block('42').subscribe();
    http.expectOne(r => r.method === 'POST' && r.url === '/api/v1/security/users/42/block').flush(null);

    api.unblock('42').subscribe();
    http.expectOne(r => r.method === 'POST' && r.url === '/api/v1/security/users/42/unblock').flush(null);

    api.disable('42').subscribe();
    http.expectOne(r => r.method === 'POST' && r.url === '/api/v1/security/users/42/disable').flush(null);
  });
});
