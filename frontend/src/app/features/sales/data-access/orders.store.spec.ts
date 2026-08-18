import { TestBed } from '@angular/core/testing';
import { of, throwError } from 'rxjs';
import { describe, expect, it, vi } from 'vitest';
import { ToastService } from '../../../core/notifications/toast.service';
import { SalesOrder } from '../domain/order.model';
import { OrderPayments } from '../domain/payment.model';
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
    creditOverrideReason: null,
    creditOverrideBy: null,
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
          code: 'insufficient_lot_stock',
          detail: 'o lote LOTE-100/1 tem 80 unidade(s) livre(s) e foram pedidas 700',
          available: 80,
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
          code: 'promise_after_shelf_life',
          detail: 'a entrega foi prometida para 2028-01-01, depois de o lote LOTE-100/1 vencer em 2027-01-10',
          earliestBestBefore: '2027-01-10',
          lotCode: 'LOTE-100/1',
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

  it('guarda os recebimentos por pedido, e só do pedido aberto', () => {
    // Buscar de todos na listagem faria uma consulta por linha para algo que quase ninguém abre.
    const conta: OrderPayments = {
      orderId: 'o1',
      total: 120,
      received: 60,
      outstanding: 60,
      currency: 'BRL',
      payments: [
        {
          id: 'pg1',
          amount: 60,
          currency: 'BRL',
          receivedOn: '2026-08-16',
          method: 'PIX',
          note: null,
          recordedBy: 'u1',
          recordedAt: '2026-08-16T12:00:00Z',
          reversal: false,
          reversesPaymentId: null,
        },
      ],
    };
    const payments = vi.fn().mockReturnValue(of(conta));
    const { store } = setup({ payments } as Partial<SalesApi>);

    store.loadPayments('o1');

    expect(payments).toHaveBeenCalledWith('o1');
    expect(store.payments()['o1'].outstanding).toBe(60);
    expect(store.payments()['o2']).toBeUndefined();
  });

  it('recarrega a conta do pedido ao lançar o recebimento', () => {
    // O saldo é a resposta que a tela dá; deixá-lo velho faria o operador lançar o mesmo valor de novo.
    const payments = vi.fn().mockReturnValue(of({ payments: [] } as unknown as OrderPayments));
    const recordPayment = vi.fn().mockReturnValue(of({ id: 'pg1' }));
    const { store, toast } = setup({ payments, recordPayment } as Partial<SalesApi>);

    store.pay('o1', {
      amount: 60,
      currency: 'BRL',
      receivedOn: null,
      method: 'PIX',
      note: null,
    });

    expect(recordPayment).toHaveBeenCalledWith('o1', expect.objectContaining({ amount: 60 }));
    expect(toast.success).toHaveBeenCalledWith('Recebimento registrado.');
    expect(payments).toHaveBeenCalledWith('o1');
  });

  it('mostra o saldo de verdade quando o recebimento passa do que o pedido deve', () => {
    // É o que corrige o zero a mais sem o operador ficar tentando números.
    const { store, toast } = setup({
      recordPayment: () =>
        throwError(() => ({
          status: 409,
          code: 'payment_exceeds_balance',
          detail: 'o pedido deve 120.00 BRL, e o recebimento lançado é de 1200.00 BRL',
        })),
    } as Partial<SalesApi>);

    store.pay('o1', {
      amount: 1200,
      currency: 'BRL',
      receivedOn: null,
      method: 'PIX',
      note: null,
    });

    expect(toast.error).toHaveBeenCalledWith(
      'o pedido deve 120.00 BRL, e o recebimento lançado é de 1200.00 BRL',
    );
  });

  it('o estorno avisa que o original continua no histórico', () => {
    // Estorno é evento compensatório, e não edição: quem lê o aviso não vai procurar a linha apagada.
    const payments = vi.fn().mockReturnValue(of({ payments: [] } as unknown as OrderPayments));
    const reversePayment = vi.fn().mockReturnValue(of({ id: 'pg2' }));
    const { store, toast } = setup({ payments, reversePayment } as Partial<SalesApi>);

    store.reversePayment('o1', 'pg1', 'cheque devolvido');

    expect(reversePayment).toHaveBeenCalledWith('pg1', 'cheque devolvido');
    expect(toast.success).toHaveBeenCalledWith(
      'Estorno registrado. O recebimento original continua no histórico.',
    );
    expect(payments).toHaveBeenCalledWith('o1');
  });

  it('guarda a recusa por crédito com os três números, em vez de só um toast', () => {
    // É a única recusa que quem vende pode resolver ali mesmo; um toast que some faria o vendedor
    // repetir o pedido só para ler o número de novo (SAL-004).
    const { store, toast } = setup({
      placeOrder: () =>
        throwError(() => ({
          status: 409,
          code: 'credit_limit_exceeded',
          detail: 'o pedido passa do limite de crédito do cliente',
          ceiling: 200,
          committed: 120,
          requested: 120,
          currency: 'BRL',
        })),
    } as Partial<SalesApi>);

    store.place(CORPO);

    expect(store.creditRefusal()).toEqual({
      ceiling: 200,
      committed: 120,
      requested: 120,
      currency: 'BRL',
    });
    expect(toast.error).toHaveBeenCalled();
  });

  it('a recusa some na tentativa seguinte, e as outras recusas não viram recusa de crédito', () => {
    // Deixá-la na tela depois de o pedido passar faria o vendedor achar que ainda está travado.
    const { store } = setup({
      placeOrder: () =>
        throwError(() => ({ status: 409, code: 'insufficient_lot_stock', available: 3 })),
    } as Partial<SalesApi>);

    store.place(CORPO);

    expect(store.creditRefusal()).toBeNull();
  });

  it('não avisa que o pedido entrou quando o crédito recusou', () => {
    // É esse aviso que fecha o formulário. Fechá-lo no envio esconderia a recusa antes de ela chegar,
    // e os três números que explicam a recusa nunca chegariam à tela (SAL-004).
    const { store } = setup({
      placeOrder: () =>
        throwError(() => ({ status: 409, code: 'credit_limit_exceeded', ceiling: 200 })),
    } as Partial<SalesApi>);
    const naRecusa = vi.fn();

    store.place(CORPO, naRecusa);

    expect(naRecusa).not.toHaveBeenCalled();
  });

  it('avisa que o pedido entrou quando ele entrou', () => {
    const { store } = setup({
      placeOrder: () => of({ id: 'o9' }),
      orders: () => of([]),
    } as Partial<SalesApi>);
    const noSucesso = vi.fn();

    store.place(CORPO, noSucesso);

    expect(noSucesso).toHaveBeenCalled();
  });
});
