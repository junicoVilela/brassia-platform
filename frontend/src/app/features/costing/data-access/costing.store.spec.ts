import { TestBed } from '@angular/core/testing';
import { of, throwError } from 'rxjs';
import { describe, expect, it, vi } from 'vitest';
import { ToastService } from '../../../core/notifications/toast.service';
import { BatchCost } from '../domain/batch-cost.model';
import { BatchOption, CostingApi } from './costing.api';
import { CostingStore } from './costing.store';

function cost(over: Partial<BatchCost> = {}): BatchCost {
  return {
    batchId: 'b1',
    batchCode: 'LOTE-100',
    closed: false,
    incomplete: true,
    volumeLiters: 390,
    total: 195,
    costPerLiter: 0.5,
    totalByCategory: { INGREDIENT: 100, PACKAGING: 95 },
    lines: [
      {
        category: 'INGREDIENT',
        description: 'Malte Pilsen',
        source: 'consumo de brassagem — lote F-1234, preço da entrada',
        quantity: 20,
        unit: 'KG',
        unitCost: 5,
        total: 100,
      },
    ],
    gaps: [{ category: 'LABOR', reason: 'não há hora trabalhada registrada' }],
    closedAt: null,
    note: null,
    ...over,
  };
}

const BATCHES: BatchOption[] = [
  { id: 'b1', code: 'LOTE-100', recipeName: 'IPA', status: 'FERMENTING' },
  { id: 'b2', code: 'LOTE-101', recipeName: 'Pilsen', status: 'IN_PROGRESS' },
];

function setup(api: Partial<CostingApi> = {}): CostingStore {
  TestBed.configureTestingModule({
    providers: [
      CostingStore,
      {
        provide: CostingApi,
        useValue: {
          closed: () => of([]),
          batches: () => of(BATCHES),
          ofBatch: () => of(cost()),
          close: () => of(cost({ closed: true, closedAt: '2026-08-06T10:00:00Z' })),
          // A taxa da hora é lida junto com os custos (CST-001-A); nula é o estado de quem nunca a definiu.
          laborRate: () => of({ costPerHour: null }),
          saveLaborRate: () => of({ costPerHour: 50 }),
          ...api,
        },
      },
      { provide: ToastService, useValue: { success: vi.fn() } },
    ],
  });
  return TestBed.inject(CostingStore);
}

describe('CostingStore', () => {
  it('separa os lotes que ainda podem ser apurados', () => {
    const store = setup({ closed: () => of([cost({ batchId: 'b1', closed: true })]) });

    store.load();

    // O lote já fechado sai da lista de abertos: não se apura duas vezes.
    expect(store.openBatches().map(batch => batch.id)).toEqual(['b2']);
  });

  it('lista as categorias que entraram no total, na ordem em que se lê um custo', () => {
    const store = setup();
    store.load();

    store.select('b1');

    expect(store.categories()).toEqual(['INGREDIENT', 'PACKAGING']);
  });

  it('o custo selecionado é sempre relido do servidor', () => {
    const ofBatch = vi.fn(() => of(cost()));
    const store = setup({ ofBatch });
    store.load();

    store.select('b1');
    store.select('b1');
    store.select('b1');

    // Duas aberturas, duas leituras: enquanto aberto, o número muda com a produção.
    expect(ofBatch).toHaveBeenCalledTimes(2);
  });

  it('fechar troca o custo em tela pelo congelado e recarrega a lista', () => {
    const closed = vi.fn(() => of([cost({ closed: true })]));
    const store = setup({ closed });
    store.load();
    store.select('b1');

    store.close('b1', 'apuração de agosto');

    expect(store.selected()?.closed).toBe(true);
    expect(closed).toHaveBeenCalledTimes(2);
    expect(store.saving()).toBeNull();
  });

  it('traduz a recusa de fechar duas vezes', () => {
    const store = setup({ close: () => throwError(() => ({ status: 409 })) });

    store.close('b1', null);

    expect(store.actionError()).toContain('já foi fechado');
  });

  it('traduz a recusa de alçada', () => {
    const store = setup({ close: () => throwError(() => ({ status: 403 })) });

    store.close('b1', null);

    expect(store.actionError()).toContain('alçada');
  });
});
