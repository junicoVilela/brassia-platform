import { TestBed } from '@angular/core/testing';
import { of, throwError } from 'rxjs';
import { describe, expect, it, vi } from 'vitest';
import { Dashboard, IndicatorGroup, OperationalIndicator } from '../domain/dashboard.model';
import { DashboardStore, lastDays } from './dashboard.store';
import { ReportingApi } from './reporting.api';

function indicator(
  code: string,
  group: IndicatorGroup,
  over: Partial<OperationalIndicator> = {},
): OperationalIndicator {
  return {
    code,
    group,
    label: 'Rótulo',
    definition: 'Definição do indicador.',
    value: 1,
    unit: 'un',
    from: '2026-07-08T03:00:00Z',
    to: '2026-08-08T03:00:00Z',
    positional: false,
    drillDown: { resource: 'production.batches', filter: {} },
    gap: null,
    ...over,
  };
}

function dashboard(over: Partial<Dashboard> = {}): Dashboard {
  return {
    from: '2026-07-08T03:00:00Z',
    to: '2026-08-08T03:00:00Z',
    sources: 5,
    indicators: [
      indicator('producao.lotes', 'PRODUCTION'),
      indicator('producao.litros', 'PRODUCTION'),
      indicator('qualidade.desvios', 'QUALITY'),
      indicator('custo.medio', 'COST'),
    ],
    ...over,
  };
}

function setup(api: Partial<ReportingApi> = {}): DashboardStore {
  TestBed.configureTestingModule({
    providers: [
      DashboardStore,
      { provide: ReportingApi, useValue: { dashboard: () => of(dashboard()), ...api } },
    ],
  });
  return TestBed.inject(DashboardStore);
}

describe('DashboardStore', () => {
  it('agrupa preservando a ordem do servidor, sem reordenar de novo', () => {
    const store = setup();

    store.load();

    const sections = store.sections();
    expect(sections.map(section => section.group)).toEqual(['PRODUCTION', 'QUALITY', 'COST']);
    // Reordenar aqui faria os cartões trocarem de lugar entre uma tela e outra.
    expect(sections[0].indicators.map(i => i.code)).toEqual(['producao.lotes', 'producao.litros']);
  });

  it('o fim escolhido é o dia inteiro: o corte vai para a meia-noite seguinte', () => {
    const call = vi.fn(() => of(dashboard()));
    const store = setup({ dashboard: call });

    store.load({ from: '2026-08-01', to: '2026-08-31' });

    const [, to] = call.mock.calls[0] as unknown as [string, string];
    expect(new Date(to).getDate()).toBe(1);
    expect(new Date(to).getMonth()).toBe(8);
  });

  it('período invertido não vai ao servidor', () => {
    const call = vi.fn(() => of(dashboard()));
    const store = setup({ dashboard: call });

    store.load({ from: '2026-08-31', to: '2026-08-01' });

    expect(call).not.toHaveBeenCalled();
    expect(store.error()).toContain('depois do fim');
  });

  it('separa os indicadores com ressalva sem escondê-los do painel', () => {
    const store = setup({
      dashboard: () =>
        of(
          dashboard({
            indicators: [
              indicator('qualidade.conformidade', 'QUALITY', { gap: 'não houve medição' }),
              indicator('producao.lotes', 'PRODUCTION'),
            ],
          }),
        ),
    });

    store.load();

    expect(store.withGap().map(i => i.code)).toEqual(['qualidade.conformidade']);
    // O indicador com ressalva continua no painel: destacado, não removido.
    expect(store.sections().flatMap(s => s.indicators)).toHaveLength(2);
  });

  it('erro apaga o painel inteiro em vez de deixar meia verdade na tela', () => {
    const store = setup({ dashboard: () => throwError(() => ({ status: 500 })) });

    store.load();

    expect(store.dashboard()).toBeNull();
    expect(store.sections()).toEqual([]);
    expect(store.error()).toBeTruthy();
  });

  it('traduz a recusa de alçada', () => {
    const store = setup({ dashboard: () => throwError(() => ({ status: 403 })) });

    store.load();

    expect(store.error()).toContain('alçada');
  });

  it('o período padrão termina hoje', () => {
    const store = setup();

    expect(store.period().to).toBe(lastDays(0).to);
  });
});
