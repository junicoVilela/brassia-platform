import { TestBed } from '@angular/core/testing';
import { of, throwError } from 'rxjs';
import { describe, expect, it, vi } from 'vitest';
import { BatchVariance, MaterialVariance } from '../domain/batch-variance.model';
import { BatchOption, CostingApi } from './costing.api';
import { VarianceStore } from './variance.store';

function material(over: Partial<MaterialVariance> = {}): MaterialVariance {
  return {
    ingredientId: 'i1',
    name: 'Malte Pilsen',
    unit: 'KG',
    plannedQuantity: 20,
    actualQuantity: 22,
    quantityVariance: 2,
    plannedUnitCost: 5,
    actualUnitCost: 5.5,
    plannedCost: 100,
    actualCost: 121,
    priceVariance: 11,
    consumptionVariance: 10,
    totalVariance: 21,
    comparable: true,
    ...over,
  };
}

function variance(over: Partial<BatchVariance> = {}): BatchVariance {
  return {
    batchId: 'b1',
    batchCode: 'LOTE-100',
    plannedCost: 100,
    actualCost: 121,
    priceVariance: 11,
    consumptionVariance: 10,
    totalVariance: 21,
    reconciles: true,
    incomplete: false,
    materials: [material()],
    volumes: [
      {
        kind: 'YIELD',
        what: 'volume transferido ao fermentador',
        planned: 400,
        actual: 390,
        variance: -10,
        variancePercent: -2.5,
        comparable: true,
        unfavorable: true,
      },
      {
        kind: 'LOSS',
        what: 'perda na transferência',
        planned: null,
        actual: 8,
        variance: null,
        variancePercent: null,
        comparable: false,
        unfavorable: false,
      },
    ],
    gaps: [],
    ...over,
  };
}

const BATCHES: BatchOption[] = [
  { id: 'b1', code: 'LOTE-100', recipeName: 'IPA', status: 'FERMENTING' },
];

function setup(api: Partial<CostingApi> = {}): VarianceStore {
  TestBed.configureTestingModule({
    providers: [
      VarianceStore,
      {
        provide: CostingApi,
        useValue: { batches: () => of(BATCHES), variance: () => of(variance()), ...api },
      },
    ],
  });
  return TestBed.inject(VarianceStore);
}

describe('VarianceStore', () => {
  it('separa os insumos que entram no dinheiro dos que não têm base', () => {
    const store = setup({
      variance: () =>
        of(
          variance({
            materials: [
              material(),
              material({ ingredientId: 'i2', name: 'Lúpulo', plannedUnitCost: null, comparable: false }),
            ],
          }),
        ),
    });

    store.select('b1');

    expect(store.compared().map(m => m.ingredientId)).toEqual(['i1']);
    // Somar o lúpulo sem par no planejado transformaria falta de base em variação de preço.
    expect(store.withoutBaseline().map(m => m.ingredientId)).toEqual(['i2']);
  });

  it('rendimento e perda são listas diferentes: o sinal não quer dizer a mesma coisa nas duas', () => {
    const store = setup();

    store.select('b1');

    expect(store.yields()).toHaveLength(1);
    expect(store.losses()).toHaveLength(1);
    expect(store.losses()[0].comparable).toBe(false);
  });

  it('escolher o mesmo lote duas vezes fecha a comparação em vez de recarregar', () => {
    const varianceCall = vi.fn(() => of(variance()));
    const store = setup({ variance: varianceCall });

    store.select('b1');
    store.select('b1');

    expect(store.selected()).toBeNull();
    expect(varianceCall).toHaveBeenCalledTimes(1);
  });

  it('a variação é sempre relida do servidor, porque os fatos continuam mudando', () => {
    const varianceCall = vi.fn(() => of(variance()));
    const store = setup({ variance: varianceCall });

    store.select('b1');
    store.select('b1');
    store.select('b1');

    expect(varianceCall).toHaveBeenCalledTimes(2);
  });

  it('traduz a recusa de alçada, que aqui é a de ver preço de compra', () => {
    const store = setup({ variance: () => throwError(() => ({ status: 403 })) });

    store.select('b1');

    expect(store.error()).toContain('alçada própria');
    expect(store.selected()).toBeNull();
  });

  it('traduz lote inexistente', () => {
    const store = setup({ variance: () => throwError(() => ({ code: 'unknown_batch' })) });

    store.select('b1');

    expect(store.error()).toContain('não existe nesta cervejaria');
  });
});
