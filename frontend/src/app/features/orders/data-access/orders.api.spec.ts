import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { afterEach, describe, expect, it } from 'vitest';
import { OrdersApi } from './orders.api';

describe('OrdersApi (contrato de ordens de produção)', () => {
  let api: OrdersApi;
  let http: HttpTestingController;

  function setup() {
    TestBed.configureTestingModule({
      providers: [OrdersApi, provideHttpClient(), provideHttpClientTesting()],
    });
    api = TestBed.inject(OrdersApi);
    http = TestBed.inject(HttpTestingController);
  }

  afterEach(() => http.verify());

  it('lista as ordens', () => {
    setup();
    let received: readonly unknown[] | undefined;
    api.list().subscribe(page => (received = page.content));
    const req = http.expectOne('/api/v1/brew-orders');
    expect(req.request.method).toBe('GET');
    req.flush({ content: [], page: 0, size: 20, totalElements: 0, totalPages: 0 });
    expect(received).toEqual([]);
  });

  it('cria uma OP (POST)', () => {
    setup();
    api.create({ recipeId: 'r', volumeLiters: 400 }).subscribe();
    const req = http.expectOne('/api/v1/brew-orders');
    expect(req.request.method).toBe('POST');
    req.flush({ id: 'x', code: 'OP-2026-0001', status: 'DRAFT' });
  });

  it('busca o detalhe/snapshot (GET /{id})', () => {
    setup();
    api.get('o1').subscribe();
    const req = http.expectOne('/api/v1/brew-orders/o1');
    expect(req.request.method).toBe('GET');
    req.flush({ id: 'o1', code: 'OP-2026-0001' });
  });
});
