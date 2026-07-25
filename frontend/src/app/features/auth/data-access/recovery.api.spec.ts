import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { afterEach, beforeEach, describe, expect, it } from 'vitest';
import { RecoveryApi } from './recovery.api';

describe('RecoveryApi', () => {
  let api: RecoveryApi;
  let http: HttpTestingController;

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [RecoveryApi, provideHttpClient(), provideHttpClientTesting()],
    });
    api = TestBed.inject(RecoveryApi);
    http = TestBed.inject(HttpTestingController);
  });

  afterEach(() => http.verify());

  it('busca o CSRF antes de solicitar recuperação de senha', () => {
    api.forgotPassword('a@x.com').subscribe();

    http.expectOne(r => r.method === 'GET' && r.url === '/api/v1/security/csrf').flush(null);
    const req = http.expectOne('/api/v1/security/password/forgot');
    expect(req.request.method).toBe('POST');
    expect(req.request.body).toEqual({ email: 'a@x.com' });
    req.flush(null);
  });

  it('redefine a senha com token e nova senha', () => {
    api.resetPassword('tok', 'novaSenha1').subscribe();

    http.expectOne('/api/v1/security/csrf').flush(null);
    const req = http.expectOne('/api/v1/security/password/reset');
    expect(req.request.method).toBe('POST');
    expect(req.request.body).toEqual({ token: 'tok', newPassword: 'novaSenha1' });
    req.flush(null);
  });

  it('confirma a verificação de e-mail com token', () => {
    api.confirmEmailVerification('tok').subscribe();

    http.expectOne('/api/v1/security/csrf').flush(null);
    const req = http.expectOne('/api/v1/security/email-verification/confirm');
    expect(req.request.method).toBe('POST');
    expect(req.request.body).toEqual({ token: 'tok' });
    req.flush(null);
  });
});
