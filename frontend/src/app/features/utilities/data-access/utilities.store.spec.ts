import { TestBed } from '@angular/core/testing';
import { of, throwError } from 'rxjs';
import { describe, expect, it, vi } from 'vitest';
import { UtilityIndicator, UtilityReport } from '../domain/utility-indicator.model';
import { UtilitiesApi } from './utilities.api';
import { UtilitiesStore, lastDays } from './utilities.store';

function indicator(over: Partial<UtilityIndicator> = {}): UtilityIndicator {
  return {
    type: 'WATER',
    unit: 'L',
    measured: 3000,
    estimated: 0,
    total: 3000,
    perLiter: 2,
    measuredPerLiter: 2,
    fullyMeasured: true,
    coverage: [{ what: 'ciclos de limpeza encerrados', reported: 12, expected: 12, complete: true }],
    sources: ['ciclo de limpeza CIP-1'],
    ...over,
  };
}

function report(over: Partial<UtilityReport> = {}): UtilityReport {
  return {
    from: '2026-07-08T03:00:00Z',
    to: '2026-08-08T03:00:00Z',
    packagedLiters: 1500,
    indicators: [indicator()],
    ...over,
  };
}

function setup(api: Partial<UtilitiesApi> = {}): UtilitiesStore {
  TestBed.configureTestingModule({
    providers: [
      UtilitiesStore,
      { provide: UtilitiesApi, useValue: { indicators: () => of(report()), ...api } },
    ],
  });
  return TestBed.inject(UtilitiesStore);
}

describe('UtilitiesStore', () => {
  it('o fim escolhido é o dia inteiro: o corte vai para a meia-noite seguinte', () => {
    const indicators = vi.fn(() => of(report()));
    const store = setup({ indicators });

    store.load({ from: '2026-08-01', to: '2026-08-31' });

    const [from, to] = indicators.mock.calls[0] as unknown as [string, string];
    // Quem pede "até 31/08" quer o dia 31 inteiro; o backend corta em `to`, exclusivo.
    expect(new Date(from).getDate()).toBe(1);
    expect(new Date(to).getDate()).toBe(1);
    expect(new Date(to).getMonth()).toBe(8);
  });

  it('período invertido não vai ao servidor', () => {
    const indicators = vi.fn(() => of(report()));
    const store = setup({ indicators });

    store.load({ from: '2026-08-31', to: '2026-08-01' });

    expect(indicators).not.toHaveBeenCalled();
    expect(store.error()).toContain('depois do fim');
  });

  it('período sem envase é reconhecido: o por litro não existe, e não é zero', () => {
    const store = setup({
      indicators: () =>
        of(report({ packagedLiters: 0, indicators: [indicator({ perLiter: null, measuredPerLiter: null })] })),
    });

    store.load();

    expect(store.withoutPackaging()).toBe(true);
    expect(store.report()?.indicators[0].perLiter).toBeNull();
  });

  it('separa as utilidades cujo número não fala pela fábrica inteira', () => {
    const store = setup({
      indicators: () =>
        of(
          report({
            indicators: [
              indicator(),
              indicator({ type: 'CO2', unit: 'kg', fullyMeasured: false, coverage: [] }),
            ],
          }),
        ),
    });

    store.load();

    // O CO₂ não declara cobertura (UTL-001-A): não afirma completude.
    expect(store.partiallyMeasured().map(i => i.type)).toEqual(['CO2']);
  });

  it('erro limpa o relatório em vez de deixar número velho na tela', () => {
    const store = setup({ indicators: () => throwError(() => ({ status: 403 })) });

    store.load();

    expect(store.report()).toBeNull();
    expect(store.error()).toContain('alçada');
  });

  it('o período padrão termina hoje', () => {
    const store = setup();

    expect(store.period().to).toBe(lastDays(0).to);
  });
});
