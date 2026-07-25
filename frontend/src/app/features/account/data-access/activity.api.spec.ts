import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { afterEach, beforeEach, describe, expect, it } from 'vitest';
import { ActivityApi } from './activity.api';

describe('ActivityApi', () => {
  let api: ActivityApi;
  let http: HttpTestingController;

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [ActivityApi, provideHttpClient(), provideHttpClientTesting()],
    });
    api = TestBed.inject(ActivityApi);
    http = TestBed.inject(HttpTestingController);
  });

  afterEach(() => http.verify());

  it('lista as sessões ativas', () => {
    let sessions: unknown;
    api.listSessions().subscribe(s => (sessions = s));
    const req = http.expectOne('/api/v1/security/sessions');
    expect(req.request.method).toBe('GET');
    req.flush([{ ref: 'a', createdAt: '2026-01-01T00:00:00Z', lastAccessedAt: '2026-01-01T01:00:00Z', current: true }]);
    expect(sessions).toHaveLength(1);
  });

  it('revoga uma sessão pelo ref (encodado)', () => {
    api.revokeSession('a/b').subscribe();
    const req = http.expectOne('/api/v1/security/sessions/a%2Fb');
    expect(req.request.method).toBe('DELETE');
    req.flush(null);
  });

  it('encerra as demais sessões via DELETE na coleção', () => {
    api.revokeOtherSessions().subscribe();
    const req = http.expectOne('/api/v1/security/sessions');
    expect(req.request.method).toBe('DELETE');
    req.flush(null);
  });

  it('busca o histórico de login', () => {
    let history: unknown;
    api.loginHistory().subscribe(h => (history = h));
    const req = http.expectOne('/api/v1/security/login-events');
    expect(req.request.method).toBe('GET');
    req.flush([{ occurredAt: '2026-01-01T00:00:00Z', outcome: 'SUCCESS', reasonCode: null }]);
    expect(history).toHaveLength(1);
  });
});
