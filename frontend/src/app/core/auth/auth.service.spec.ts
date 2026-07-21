import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { afterEach, beforeEach, describe, expect, it } from 'vitest';
import { AuthService } from './auth.service';

describe('AuthService', () => {
  let auth: AuthService;
  let http: HttpTestingController;

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [AuthService, provideHttpClient(), provideHttpClientTesting()],
    });
    auth = TestBed.inject(AuthService);
    http = TestBed.inject(HttpTestingController);
  });

  afterEach(() => http.verify());

  it('busca o token CSRF antes de autenticar e guarda o principal', () => {
    let result: unknown;
    auth.login({ email: 'a@x.com', password: 'segredo1' }).subscribe(u => (result = u));

    http.expectOne(r => r.method === 'GET' && r.url === '/api/v1/security/csrf').flush(null);
    const login = http.expectOne(r => r.method === 'POST' && r.url === '/api/v1/security/login');
    expect(login.request.body).toEqual({ email: 'a@x.com', password: 'segredo1' });
    login.flush({ userId: '1', displayName: 'Ana', brewery: null, permissions: [] });

    expect(auth.isAuthenticated()).toBe(true);
    expect(auth.user()?.displayName).toBe('Ana');
    expect(result).toBeTruthy();
  });

  it('ensureSession retorna null e não autentica quando o servidor responde 401', () => {
    let result: unknown = 'x';
    auth.ensureSession().subscribe(u => (result = u));

    http.expectOne('/api/v1/security/session').flush(null, { status: 401, statusText: 'Unauthorized' });

    expect(result).toBeNull();
    expect(auth.isAuthenticated()).toBe(false);
  });

  it('ensureSession consulta o servidor apenas uma vez', () => {
    auth.ensureSession().subscribe();
    http.expectOne('/api/v1/security/session').flush({ userId: '1', displayName: 'Ana', brewery: null, permissions: [] });

    auth.ensureSession().subscribe();
    http.expectNone('/api/v1/security/session');
    expect(auth.isAuthenticated()).toBe(true);
  });
});
