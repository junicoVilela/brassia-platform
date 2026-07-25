import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { afterEach, beforeEach, describe, expect, it } from 'vitest';
import { MfaApi } from './mfa.api';

describe('MfaApi', () => {
  let api: MfaApi;
  let http: HttpTestingController;

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [MfaApi, provideHttpClient(), provideHttpClientTesting()],
    });
    api = TestBed.inject(MfaApi);
    http = TestBed.inject(HttpTestingController);
  });

  afterEach(() => http.verify());

  it('inicia o enroll no endpoint de TOTP', () => {
    let enrollment: unknown;
    api.enroll().subscribe(e => (enrollment = e));

    const req = http.expectOne('/api/v1/security/totp/enroll');
    expect(req.request.method).toBe('POST');
    req.flush({ secret: 'ABC123', otpauthUri: 'otpauth://totp/BrassIA' });

    expect(enrollment).toEqual({ secret: 'ABC123', otpauthUri: 'otpauth://totp/BrassIA' });
  });

  it('confirma o código de 6 dígitos', () => {
    api.confirm('123456').subscribe();
    const req = http.expectOne('/api/v1/security/totp/confirm');
    expect(req.request.method).toBe('POST');
    expect(req.request.body).toEqual({ code: '123456' });
    req.flush(null);
  });

  it('desativa via DELETE, com senha quando informada', () => {
    api.disable('secret-pass').subscribe();
    const req = http.expectOne('/api/v1/security/totp');
    expect(req.request.method).toBe('DELETE');
    expect(req.request.body).toEqual({ currentPassword: 'secret-pass' });
    req.flush(null);
  });

  it('regenera os códigos de recuperação', () => {
    let codes: unknown;
    api.regenerateRecoveryCodes().subscribe(r => (codes = r.codes));
    const req = http.expectOne('/api/v1/security/recovery-codes/regenerate');
    expect(req.request.method).toBe('POST');
    req.flush({ codes: ['aaa', 'bbb'] });

    expect(codes).toEqual(['aaa', 'bbb']);
  });
});
