import { TestBed } from '@angular/core/testing';
import { of, throwError } from 'rxjs';
import { describe, expect, it, vi } from 'vitest';
import { ToastService } from '../../../core/notifications/toast.service';
import { CatalogItem, PortalOrder } from '../domain/portal.model';
import { PortalApi } from './portal.api';
import { PortalStore } from './portal.store';

const CATALOGO: CatalogItem[] = [
  {
    productId: 'p1',
    sku: 'IPA-473',
    name: 'IPA lata 473 ml',
    unitAmount: 12,
    currency: 'BRL',
    taxIncluded: false,
    availableUnits: 780,
  },
];

function order(over: Partial<PortalOrder> = {}): PortalOrder {
  return {
    id: 'o1',
    code: 'POR-1',
    status: 'PLACED',
    placedOn: '2026-08-15',
    promisedFor: null,
    total: 120,
    currency: 'BRL',
    lines: [{ sku: 'IPA-473', quantity: 10, unitAmount: 12, currency: 'BRL' }],
    ...over,
  };
}

function setup(api: Partial<PortalApi>) {
  const toast = { success: vi.fn(), error: vi.fn() };
  api.catalog ??= () => of(CATALOGO);
  api.orders ??= () => of([order()]);
  api.credit ??= () => of({ ceiling: null, currency: null, committed: 0 });
  TestBed.configureTestingModule({
    providers: [
      PortalStore,
      { provide: PortalApi, useValue: api },
      { provide: ToastService, useValue: toast },
    ],
  });
  return { store: TestBed.inject(PortalStore), toast };
}

describe('PortalStore', () => {
  it('carrega catálogo, pedidos e crédito de uma vez', () => {
    const { store } = setup({});

    store.load();

    expect(store.catalog()).toHaveLength(1);
    expect(store.orders()).toHaveLength(1);
    expect(store.credit()).not.toBeNull();
  });

  it('sem teto, o quanto ainda cabe é nulo e não zero', () => {
    // Zero faria a tela dizer que o cliente não pode comprar nada, quando na verdade não há limite.
    const { store } = setup({});

    store.load();

    expect(store.remaining()).toBeNull();
  });

  it('com teto, calcula o quanto ainda cabe', () => {
    const { store } = setup({
      credit: () => of({ ceiling: 1000, currency: 'BRL', committed: 400 }),
    } as Partial<PortalApi>);

    store.load();

    expect(store.remaining()).toBe(600);
  });

  it('relê o catálogo depois de comprar, porque o pedido consumiu disponibilidade', () => {
    // Uma lista em cache ofereceria unidades que já têm dono.
    const catalog = vi.fn().mockReturnValue(of(CATALOGO));
    const place = vi.fn().mockReturnValue(of({ id: 'o9' }));
    const { store } = setup({ catalog, place } as Partial<PortalApi>);
    store.load();
    catalog.mockClear();

    store.place('POR-2', 'p1', 10, null);

    expect(catalog).toHaveBeenCalled();
  });

  it('mostra teto, comprometido e pedido quando o crédito estoura', () => {
    const { store, toast } = setup({
      place: () =>
        throwError(() => ({
          status: 409,
          code: 'credit_limit_exceeded',
          detail: 'o pedido de 120.0000 BRL passa do limite de 200.00 BRL, com 120.0000 BRL já comprometido',
          ceiling: 200,
          committed: 120,
        })),
    } as Partial<PortalApi>);

    store.place('POR-2', 'p1', 10, null);

    expect(toast.error).toHaveBeenCalledWith(
      'o pedido de 120.0000 BRL passa do limite de 200.00 BRL, com 120.0000 BRL já comprometido',
    );
  });

  it('a recompra avisa que o preço é o de hoje', () => {
    // Repete a intenção, e não o valor — o cliente não deve esperar o total antigo.
    const reorder = vi.fn().mockReturnValue(of({ id: 'o9' }));
    const { store, toast } = setup({ reorder } as Partial<PortalApi>);

    store.reorder(order(), 'POR-1-R');

    expect(reorder).toHaveBeenCalledWith('o1', { code: 'POR-1-R', promisedFor: null }, expect.any(String));
    expect(toast.success).toHaveBeenCalledWith('Recompra enviada, com o preço de hoje.');
  });
});
