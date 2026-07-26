import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { afterEach, describe, expect, it } from 'vitest';
import { CountsApi } from './counts.api';

describe('CountsApi (contrato de inventário físico)', () => {
  let api: CountsApi;
  let http: HttpTestingController;

  function setup() {
    TestBed.configureTestingModule({
      providers: [CountsApi, provideHttpClient(), provideHttpClientTesting()],
    });
    api = TestBed.inject(CountsApi);
    http = TestBed.inject(HttpTestingController);
  }

  afterEach(() => http.verify());

  it('cria contagem', () => {
    setup();
    api.create({ lines: [{ lotId: 'l1', countedQuantity: 20 }] }).subscribe();
    const req = http.expectOne('/api/v1/inventory/counts');
    expect(req.request.method).toBe('POST');
    req.flush({ id: 'c1', status: 'OPEN' });
  });

  it('aprova contagem', () => {
    setup();
    api.approve('c1').subscribe();
    const req = http.expectOne('/api/v1/inventory/counts/c1/approve');
    expect(req.request.method).toBe('POST');
    req.flush({ id: 'c1', status: 'APPROVED', adjustments: 1 });
  });
});
