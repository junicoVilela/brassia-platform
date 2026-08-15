import { TestBed } from '@angular/core/testing';
import { of } from 'rxjs';
import { describe, expect, it, vi } from 'vitest';
import { SalesApi } from '../../sales/data-access/sales.api';
import { Product } from '../../sales/domain/product.model';
import { DemandForecast } from '../domain/forecast.model';
import { ForecastApi } from './forecast.api';
import { ForecastStore } from './forecast.store';

function product(): Product {
  return {
    id: 'p1',
    sku: 'IPA-473',
    name: 'IPA lata 473 ml',
    recipeId: 'r1',
    containerId: 'c1',
    active: true,
  };
}

function forecast(over: Partial<DemandForecast> = {}): DemandForecast {
  return {
    productId: 'p1',
    forMonth: '2026-09',
    hasNumbers: true,
    expectedUnits: 100,
    lowerBound: 90,
    upperBound: 110,
    sampleMonths: 6,
    method: 'moving-average v1',
    meanAbsolutePercentageError: 8,
    confidence: 'MODERATE',
    ...over,
  };
}

function setup(api: Partial<ForecastApi>) {
  TestBed.configureTestingModule({
    providers: [
      ForecastStore,
      { provide: ForecastApi, useValue: api },
      { provide: SalesApi, useValue: { products: () => of([product()]) } },
    ],
  });
  return TestBed.inject(ForecastStore);
}

describe('ForecastStore', () => {
  it('busca a previsão do produto selecionado', () => {
    const demand = vi.fn().mockReturnValue(of(forecast()));
    const store = setup({ demand } as Partial<ForecastApi>);
    store.load();

    store.select(product());

    expect(demand).toHaveBeenCalledWith('p1');
    expect(store.forecast()?.expectedUnits).toBe(100);
  });

  it('a faixa é expressa em percentual da média', () => {
    // É o que impede a média de ser lida como promessa: 20% de faixa e 200% de faixa dão o mesmo
    // número no centro e significam coisas opostas.
    const store = setup({ demand: () => of(forecast()) } as Partial<ForecastApi>);
    store.load();

    store.select(product());

    expect(store.spreadPercent()).toBe(20);
  });

  it('sem previsão, não há faixa a calcular', () => {
    // E não zero: zero pareceria uma faixa estreitíssima, que é o oposto do que INSUFFICIENT diz.
    const store = setup({
      demand: () =>
        of(
          forecast({
            hasNumbers: false,
            expectedUnits: null,
            lowerBound: null,
            upperBound: null,
            meanAbsolutePercentageError: null,
            confidence: 'INSUFFICIENT',
            sampleMonths: 2,
          }),
        ),
    } as Partial<ForecastApi>);
    store.load();

    store.select(product());

    expect(store.spreadPercent()).toBeNull();
    expect(store.forecast()?.hasNumbers).toBe(false);
  });

  it('limpa a previsão anterior ao trocar de produto', () => {
    // Sem isto, a tela mostraria por um instante o número do produto anterior sob o nome do novo.
    let entregas = 0;
    const store = setup({
      demand: () => {
        entregas++;
        return entregas === 1 ? of(forecast()) : of(forecast({ expectedUnits: 500 }));
      },
    } as Partial<ForecastApi>);
    store.load();

    store.select(product());
    expect(store.forecast()?.expectedUnits).toBe(100);

    store.select({ ...product(), id: 'p2' });
    expect(store.forecast()?.expectedUnits).toBe(500);
  });
});
