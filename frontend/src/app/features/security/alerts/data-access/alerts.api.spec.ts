import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { afterEach, beforeEach, describe, expect, it } from 'vitest';
import { AlertsApi } from './alerts.api';

describe('AlertsApi', () => {
  let api: AlertsApi;
  let http: HttpTestingController;

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [AlertsApi, provideHttpClient(), provideHttpClientTesting()],
    });
    api = TestBed.inject(AlertsApi);
    http = TestBed.inject(HttpTestingController);
  });

  afterEach(() => http.verify());

  it('lista sem filtro', () => {
    api.list().subscribe();
    const req = http.expectOne(r => r.url === '/api/v1/security/alerts');
    expect(req.request.method).toBe('GET');
    expect(req.request.params.has('status')).toBe(false);
    req.flush([]);
  });

  it('lista filtrando por estado', () => {
    api.list('OPEN').subscribe();
    const req = http.expectOne(r => r.url === '/api/v1/security/alerts');
    expect(req.request.params.get('status')).toBe('OPEN');
    req.flush([]);
  });

  it('atualiza o estado via PATCH', () => {
    api.updateStatus('a1', 'RESOLVED').subscribe();
    const req = http.expectOne('/api/v1/security/alerts/a1');
    expect(req.request.method).toBe('PATCH');
    expect(req.request.body).toEqual({ status: 'RESOLVED' });
    req.flush(null);
  });
});
