import { TestBed } from '@angular/core/testing';
import { of, throwError } from 'rxjs';
import { describe, expect, it, vi } from 'vitest';
import { ToastService } from '../../../core/notifications/toast.service';
import { PriceEntry, Product, SalesChannel, SellableLot } from '../domain/product.model';
import { SalesApi } from './sales.api';
import { SalesStore } from './sales.store';

function product(over: Partial<Product> = {}): Product {
  return {
    id: 'p1',
    sku: 'IPA-473',
    name: 'IPA lata 473 ml',
    recipeId: 'r1',
    containerId: 'c1',
    active: true,
    ...over,
  };
}

const CANAIS: SalesChannel[] = [
  { id: 'ch1', code: 'TAPROOM', name: 'Taproom', active: true },
  { id: 'ch2', code: 'DIST', name: 'Distribuidor', active: true },
];

const VIGENCIAS: PriceEntry[] = [
  { amount: 12, currency: 'BRL', taxIncluded: false, validFrom: '2026-01-01', validTo: '2026-02-28' },
  { amount: 14, currency: 'BRL', taxIncluded: false, validFrom: '2026-03-01', validTo: null },
];

const LOTES: SellableLot[] = [
  {
    finishedLotId: 'l1',
    code: 'LOTE-100/1',
    batchCode: 'LOTE-100',
    units: 780,
    containerVolumeMl: 355,
    packagedOn: '2026-01-10',
    bestBefore: '2026-07-10',
  },
];

function setup(api: Partial<SalesApi>) {
  const toast = { success: vi.fn(), error: vi.fn() };
  // Toda sele\u00e7\u00e3o de produto busca os lotes vend\u00e1veis; sem o stub, os testes de pre\u00e7o quebrariam por
  // um motivo que nada tem a ver com pre\u00e7o.
  api.sellableLots ??= () => of(LOTES);
  TestBed.configureTestingModule({
    providers: [
      SalesStore,
      { provide: SalesApi, useValue: api },
      { provide: ToastService, useValue: toast },
    ],
  });
  return { store: TestBed.inject(SalesStore), toast };
}

describe('SalesStore', () => {
  it('carrega catálogo e já seleciona o primeiro canal', () => {
    // Sem canal não há onde precificar: preço é sempre por produto e canal.
    const { store } = setup({
      products: () => of([product()]),
      channels: () => of(CANAIS),
    } as Partial<SalesApi>);

    store.load();

    expect(store.products()).toHaveLength(1);
    expect(store.selectedChannel()).toBe('ch1');
  });

  it('o preço vigente é o único sem fim', () => {
    const { store } = setup({
      products: () => of([product()]),
      channels: () => of(CANAIS),
      priceSchedule: () => of({ productId: 'p1', channelId: 'ch1', entries: VIGENCIAS }),
    } as Partial<SalesApi>);
    store.load();
    store.selectProduct(product());

    expect(store.currentPrice()?.amount).toBe(14);
    expect(store.priceEntries()).toHaveLength(2);
  });

  it('sem preço no canal, o vigente é nulo e não zero', () => {
    // Zero faria uma venda sair de graça. A tela mostra isso como lacuna.
    const { store } = setup({
      products: () => of([product()]),
      channels: () => of(CANAIS),
      priceSchedule: () => of({ productId: 'p1', channelId: 'ch1', entries: [] }),
    } as Partial<SalesApi>);
    store.load();
    store.selectProduct(product());

    expect(store.currentPrice()).toBeNull();
  });

  it('relê a linha do tempo do servidor depois de um preço novo', () => {
    // Cadastrar um preço fecha o anterior na véspera. Refazer essa regra no cliente seria manter duas
    // implementações da mesma coisa.
    const priceSchedule = vi.fn().mockReturnValue(of({ productId: 'p1', channelId: 'ch1', entries: VIGENCIAS }));
    const priceFrom = vi.fn().mockReturnValue(of(void 0));
    const { store } = setup({
      products: () => of([product()]),
      channels: () => of(CANAIS),
      priceSchedule,
      priceFrom,
    } as Partial<SalesApi>);
    store.load();
    store.selectProduct(product());
    priceSchedule.mockClear();

    store.priceFrom(15, 'BRL', false, '2026-06-01');

    expect(priceFrom).toHaveBeenCalledWith('p1', {
      channelId: 'ch1',
      amount: 15,
      currency: 'BRL',
      taxIncluded: false,
      validFrom: '2026-06-01',
    });
    expect(priceSchedule).toHaveBeenCalledWith('p1', 'ch1');
  });

  it('mostra a mensagem do servidor, que traz a data da sobreposição', () => {
    const { store, toast } = setup({
      products: () => of([product()]),
      channels: () => of(CANAIS),
      priceSchedule: () => of({ productId: 'p1', channelId: 'ch1', entries: VIGENCIAS }),
      priceFrom: () =>
        throwError(() => ({
          status: 409,
          error: {
            code: 'sales_price_overlap',
            detail: 'já existe preço vigente para este produto e canal em 2026-02-01',
            from: '2026-02-01',
          },
        })),
    } as Partial<SalesApi>);
    store.load();
    store.selectProduct(product());

    store.priceFrom(13, 'BRL', false, '2026-02-01');

    expect(toast.error).toHaveBeenCalledWith(
      'já existe preço vigente para este produto e canal em 2026-02-01',
    );
  });

  it('carrega os lotes vendáveis ao selecionar o produto', () => {
    // Vendável é liberado, dentro da validade e sem quarentena — o backend compõe, o cliente confia.
    const sellableLots = vi.fn().mockReturnValue(of(LOTES));
    const { store } = setup({
      products: () => of([product()]),
      channels: () => of(CANAIS),
      priceSchedule: () => of({ productId: 'p1', channelId: 'ch1', entries: VIGENCIAS }),
      sellableLots,
    } as Partial<SalesApi>);
    store.load();
    store.selectProduct(product());

    expect(sellableLots).toHaveBeenCalledWith('p1');
    expect(store.sellableLots()).toHaveLength(1);
  });

  it('lista vazia de lotes é informação, e não erro', () => {
    // Há produto e preço, mas nada liberado — e é aí que alguém precisa ir atrás da qualidade.
    const { store } = setup({
      products: () => of([product()]),
      channels: () => of(CANAIS),
      priceSchedule: () => of({ productId: 'p1', channelId: 'ch1', entries: VIGENCIAS }),
      sellableLots: () => of([]),
    } as Partial<SalesApi>);
    store.load();
    store.selectProduct(product());

    expect(store.sellableLots()).toEqual([]);
  });

  it('não tenta precificar sem produto ou canal selecionado', () => {
    const priceFrom = vi.fn();
    const { store } = setup({ priceFrom } as Partial<SalesApi>);

    store.priceFrom(10, 'BRL', false, '2026-01-01');

    expect(priceFrom).not.toHaveBeenCalled();
  });
});
