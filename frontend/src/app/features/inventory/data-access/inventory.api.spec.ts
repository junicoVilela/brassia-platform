import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { afterEach, describe, expect, it } from 'vitest';
import { InventoryApi } from './inventory.api';

describe('InventoryApi (contrato de estoque/ledger)', () => {
  let api: InventoryApi;
  let http: HttpTestingController;

  function setup() {
    TestBed.configureTestingModule({
      providers: [InventoryApi, provideHttpClient(), provideHttpClientTesting()],
    });
    api = TestBed.inject(InventoryApi);
    http = TestBed.inject(HttpTestingController);
  }

  afterEach(() => http.verify());

  it('consulta o saldo do lote', () => {
    setup();
    api.balance('l1').subscribe();
    const req = http.expectOne('/api/v1/inventory/lots/l1/balance');
    expect(req.request.method).toBe('GET');
    req.flush({ onHand: 25, reserved: 0, available: 25 });
  });

  it('lista o ledger do lote', () => {
    setup();
    api.movements('l1').subscribe();
    const req = http.expectOne('/api/v1/inventory/lots/l1/movements');
    expect(req.request.method).toBe('GET');
    req.flush([]);
  });

  it('registra um movimento', () => {
    setup();
    api.recordMovement('l1', { type: 'CONSUMPTION', quantity: 10 }).subscribe();
    const req = http.expectOne('/api/v1/inventory/lots/l1/movements');
    expect(req.request.method).toBe('POST');
    expect(req.request.body).toEqual({ type: 'CONSUMPTION', quantity: 10 });
    req.flush({ onHand: 15, reserved: 0, available: 15 });
  });
});
