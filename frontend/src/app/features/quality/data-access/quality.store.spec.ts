import { TestBed } from '@angular/core/testing';
import { of, throwError } from 'rxjs';
import { describe, expect, it, vi } from 'vitest';
import { ToastService } from '../../../core/notifications/toast.service';
import { ControlPlan, Deviation } from '../domain/quality.model';
import { QualityApi } from './quality.api';
import { QualityStore } from './quality.store';

function plan(over: Partial<ControlPlan> = {}): ControlPlan {
  return {
    id: 'p1',
    code: 'PC-001',
    name: 'Controle de mosto',
    recipeId: null,
    stage: 'BREWING',
    stageLabel: 'Brassagem',
    status: 'PUBLISHED',
    version: 1,
    points: [],
    ...over,
  };
}

function deviation(over: Partial<Deviation> = {}): Deviation {
  return {
    id: 'd1',
    measurementId: 'm1',
    planId: 'p1',
    pointId: 'pt1',
    parameter: 'pH do mosto',
    severity: 'MAJOR',
    severityLabel: 'Grave',
    bound: 'ABOVE_MAX',
    limitValue: 5.5,
    measuredValue: 6.2,
    excess: 0.7,
    unit: 'pH',
    action: 'Ajustar e remedir',
    status: 'OPEN',
    description: 'pH do mosto medido em 6.2 pH, acima do limite de 5.5 pH',
    openedAt: '2026-08-03T12:00:00Z',
    ...over,
  };
}

function setup(api: Partial<QualityApi>, toast = { success: vi.fn(), error: vi.fn() }): QualityStore {
  TestBed.configureTestingModule({
    providers: [
      QualityStore,
      { provide: QualityApi, useValue: api },
      { provide: ToastService, useValue: toast },
    ],
  });
  return TestBed.inject(QualityStore);
}

describe('QualityStore', () => {
  it('carrega planos e desvios', () => {
    const store = setup({ plans: () => of([plan()]), deviations: () => of([deviation()]) });

    store.load();

    expect(store.plans()).toHaveLength(1);
    expect(store.deviations()).toHaveLength(1);
    expect(store.empty()).toBe(false);
  });

  it('só oferece plano publicado para medir', () => {
    const store = setup({
      plans: () => of([plan(), plan({ id: 'p2', code: 'PC-002', status: 'DRAFT' })]),
      deviations: () => of([]),
    });

    store.load();

    expect(store.publishedPlans().map(p => p.code)).toEqual(['PC-001']);
  });

  it('destaca só os desvios que não são leves', () => {
    const store = setup({
      plans: () => of([]),
      deviations: () =>
        of([deviation(), deviation({ id: 'd2', severity: 'MINOR', severityLabel: 'Leve' })]),
    });

    store.load();

    expect(store.severeDeviations().map(d => d.id)).toEqual(['d1']);
  });

  it('avisa na hora quando a medição abre desvio, com a ação prescrita', () => {
    // Descobrir o desvio numa listagem depois é tarde: quem mediu precisa saber agora.
    const toast = { success: vi.fn(), error: vi.fn() };
    const store = setup(
      {
        plans: () => of([]),
        deviations: () => of([]),
        measure: () =>
          of({ measurementId: 'm1', withinSpec: false, deviationId: 'd1', deviation: deviation() }),
      },
      toast,
    );

    store.measure({
      planId: 'p1',
      pointId: 'pt1',
      batchId: null,
      instrumentId: null,
      value: 6.2,
      note: null,
      measuredAt: null,
    });

    expect(toast.error).toHaveBeenCalled();
    expect(toast.error.mock.calls[0][0]).toContain('Ajustar e remedir');
    expect(toast.success).not.toHaveBeenCalled();
  });

  it('guarda a recusa do ponto crítico separada do erro genérico', () => {
    const store = setup({
      plans: () => of([]),
      deviations: () => of([]),
      measure: () =>
        throwError(() => ({
          status: 409,
          error: {
            code: 'instrument_not_fit',
            controlPoint: { parameter: 'pH do mosto', instrument: 'PH-01', fitness: 'EXPIRED' },
          },
        })),
    });

    store.measure({
      planId: 'p1',
      pointId: 'pt1',
      batchId: null,
      instrumentId: 'i1',
      value: 5.0,
      note: null,
      measuredAt: null,
    });

    expect(store.criticalRefusal()?.fitness).toBe('EXPIRED');
    expect(store.measurementError()).toBeNull();
  });

  it('abre e fecha o mesmo plano', () => {
    const store = setup({ plans: () => of([]), deviations: () => of([]), measurements: () => of([]) });

    store.togglePlan(plan());
    expect(store.openPlanOf()).toBe('p1');

    store.togglePlan(plan());
    expect(store.openPlanOf()).toBeNull();
  });

  it('reporta erro de carregamento sem apagar a tela', () => {
    const store = setup({ plans: () => throwError(() => ({ status: 500 })), deviations: () => of([]) });

    store.load();

    expect(store.error()).toBe('Não foi possível carregar os planos de controle.');
    expect(store.loading()).toBe(false);
  });
});
