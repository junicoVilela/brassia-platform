import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { afterEach, beforeEach, describe, expect, it } from 'vitest';
import { AuthApi } from './auth.api';
import { LoginResult } from './session-user.model';

describe('AuthApi', () => {
  let api: AuthApi;
  let http: HttpTestingController;

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [AuthApi, provideHttpClient(), provideHttpClientTesting()],
    });
    api = TestBed.inject(AuthApi);
    http = TestBed.inject(HttpTestingController);
  });

  afterEach(() => http.verify());

  it('devolve MFA_REQUIRED quando o login pede segundo fator', () => {
    let result: LoginResult | undefined;
    api.login({ email: 'a@x.com', password: 's' }).subscribe(r => (result = r));

    const req = http.expectOne('/api/v1/security/login');
    expect(req.request.method).toBe('POST');
    req.flush({ status: 'MFA_REQUIRED', methods: ['TOTP', 'RECOVERY_CODE'] });

    expect(result).toEqual({ status: 'MFA_REQUIRED', methods: ['TOTP', 'RECOVERY_CODE'] });
  });

  it('conclui o segundo fator no endpoint /login/mfa', () => {
    api.completeMfa({ code: '123456', method: 'TOTP' }).subscribe();

    const req = http.expectOne('/api/v1/security/login/mfa');
    expect(req.request.method).toBe('POST');
    expect(req.request.body).toEqual({ code: '123456', method: 'TOTP' });
    req.flush({ userId: '1', displayName: 'Ana', activeBrewery: null, accessibleBreweries: [], permissions: [] });
  });
});
