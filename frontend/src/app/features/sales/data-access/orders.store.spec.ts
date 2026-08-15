import { TestBed } from '@angular/core/testing';
import { of, throwError } from 'rxjs';
import { describe, expect, it, vi } from 'vitest';
import { ToastService } from '../../../core/notifications/toast.service';
import { SalesOrder } from '../domain/order.model';
import { OrdersStore } from './orders.store';
import { SalesApi } from './sales.api';

function order(over: Partial<SalesOrder> = {}): SalesOrder {
  return {
    id: 'o1',
    code: 'PED-1',
    customerId: 'c1',
    channelId: 'ch1',
    status: 'PLACED',
    placedOn: '2026-08-15',
    promisedFor: null,
    total: 120,
    currency: 'BRL',
    lines: [
      {
        productId: 'p1',
        sku: 'IPA-473',
        quantity: 10,
        unitAmount: 12,
        currency: 'BRL',
        taxIncluded: false,
        reservations: [
          { finishedLotId: 'l1', lotCode: 'LOTE-100/1', units: 10, bestBefore: '2027-01-10' },
        ],
      },
    ],
    ...over,
  };
}

function setup(api: Partial<SalesApi>) {
  const toast = { success: vi.fn(), error: vi.fn() };
  TestBed.configureTestingModule({
    providers: [
      OrdersStore,
      { provide: SalesApi, useValue: api },
      { provide: ToastService, useValue: toast },
    ],
  });
  return { store: TestBed.inject(OrdersStore), toast };
}

const CORPO = {
  code: 'PED-2',
  customerId: 'c1',
  channelId: 'ch1',
  promisedFor: null,
  items: [{ productId: 'p1', quantity: 10 }],
};

describe('OrdersStore', () => {
  it('carrega pedidos e separa os confirmados', () => {
    const { store } = setup({
      orders: () => of([order(), order({ id: 'o2', status: 'CANCELLED' })]),
    } as Partial<SalesApi>);

    store.load();

    expect(store.orders()).toHaveLength(2);
    expect(store.open()).toHaveLength(1);
  });

  it('gera uma chave de idempotência nova a cada tentativa de envio', () => {
    // Chave fixa por formulário faria o operador que corrige a quantidade e reenvia receber de volta
    // o pedido antigo, com o valor errado — e concluir que o sistema ignorou a correção.
    const placeOrder = vi.fn().mockReturnValue(of({ id: 'o9' }));
    const { store } = setup({ orders: () => of([]), placeOrder } as Partial<SalesApi>);

    store.place(CORPO);
    store.place({ ...CORPO, items: [{ productId: 'p1', quantity: 20 }] });

    const primeira = placeOrder.mock.calls[0][1];
    const segunda = placeOrder.mock.calls[1][1];
    expect(primeira).toBeTruthy();
    expect(segunda).not.toBe(primeira);
  });

  it('mostra a mensagem do servidor quando o estoque acaba', () => {
    // A mensagem traz quanto sobrou; sem ela o operador fica tentando números até um passar.
    const { store, toast } = setup({
      orders: () => of([]),
      placeOrder: () =>
        throwError(() => ({
          status: 409,
          error: {
            code: 'insufficient_lot_stock',
            detail: 'o lote LOTE-100/1 tem 80 unidade(s) livre(s) e foram pedidas 700',
            available: 80,
          },
        })),
    } as Partial<SalesApi>);

    store.place(CORPO);

    expect(toast.error).toHaveBeenCalledWith(
      'o lote LOTE-100/1 tem 80 unidade(s) livre(s) e foram pedidas 700',
    );
  });

  it('mostra até quando dá para prometer quando a data passa da validade', () => {
    const { store, toast } = setup({
      orders: () => of([]),
      placeOrder: () =>
        throwError(() => ({
          status: 409,
          error: {
            code: 'promise_after_shelf_life',
            detail: 'a entrega foi prometida para 2028-01-01, depois de o lote LOTE-100/1 vencer em 2027-01-10',
            earliestBestBefore: '2027-01-10',
            lotCode: 'LOTE-100/1',
          },
        })),
    } as Partial<SalesApi>);

    store.place({ ...CORPO, promisedFor: '2028-01-01' });

    expect(toast.error).toHaveBeenCalledWith(
      'a entrega foi prometida para 2028-01-01, depois de o lote LOTE-100/1 vencer em 2027-01-10',
    );
  });

  it('recarrega a lista ao cancelar, porque o estoque volta', () => {
    const orders = vi.fn().mockReturnValue(of([]));
    const cancelOrder = vi.fn().mockReturnValue(of(void 0));
    const { store, toast } = setup({ orders, cancelOrder } as Partial<SalesApi>);

    store.cancel(order());

    expect(cancelOrder).toHaveBeenCalledWith('o1');
    expect(toast.success).toHaveBeenCalledWith('Pedido cancelado e estoque devolvido.');
    expect(orders).toHaveBeenCalled();
  });
});
