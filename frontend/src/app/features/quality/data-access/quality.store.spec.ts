import { TestBed } from '@angular/core/testing';
import { of, throwError } from 'rxjs';
import { describe, expect, it, vi } from 'vitest';
import { ToastService } from '../../../core/notifications/toast.service';
import { ControlPlan, Deviation, NonConformity } from '../domain/quality.model';
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

function nc(over: Partial<NonConformity> = {}): NonConformity {
  return {
    id: 'nc1',
    code: 'NC-001',
    title: 'pH fora da faixa',
    description: 'd',
    source: 'DEVIATION',
    sourceLabel: 'Desvio de medição',
    deviationId: 'd1',
    batchId: null,
    severity: 'MAJOR',
    severityLabel: 'Grave',
    status: 'ACTION_PLANNED',
    statusLabel: 'Ação planejada',
    containmentDueOn: '2026-08-04',
    investigationDueOn: '2026-08-08',
    verificationDueOn: '2026-09-02',
    overduePhases: [],
    overdue: false,
    closable: false,
    containment: null,
    investigation: null,
    actions: [],
    verifications: [],
    openedAt: '2026-08-03T12:00:00Z',
    closedAt: null,
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
    const store = setup({
      plans: () => of([plan()]),
      deviations: () => of([deviation()]),
      nonConformities: () => of([]),
    });

    store.load();

    expect(store.plans()).toHaveLength(1);
    expect(store.deviations()).toHaveLength(1);
    expect(store.empty()).toBe(false);
  });

  it('só oferece plano publicado para medir', () => {
    const store = setup({
      plans: () => of([plan(), plan({ id: 'p2', code: 'PC-002', status: 'DRAFT' })]),
      deviations: () => of([]),
      nonConformities: () => of([]),
    });

    store.load();

    expect(store.publishedPlans().map(p => p.code)).toEqual(['PC-001']);
  });

  it('destaca só os desvios que não são leves', () => {
    const store = setup({
      plans: () => of([]),
      deviations: () =>
        of([deviation(), deviation({ id: 'd2', severity: 'MINOR', severityLabel: 'Leve' })]),
      nonConformities: () => of([]),
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
        nonConformities: () => of([]),
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
      nonConformities: () => of([]),
      measure: () =>
        throwError(() => ({
          status: 409,
          code: 'instrument_not_fit',
            controlPoint: { parameter: 'pH do mosto', instrument: 'PH-01', fitness: 'EXPIRED' }
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
    const store = setup({
      plans: () => of([]),
      deviations: () => of([]),
      nonConformities: () => of([]),
      measurements: () => of([]),
    });

    store.togglePlan(plan());
    expect(store.openPlanOf()).toBe('p1');

    store.togglePlan(plan());
    expect(store.openPlanOf()).toBeNull();
  });

  it('separa as não conformidades vencidas e as prontas para encerrar', () => {
    const store = setup({
      plans: () => of([]),
      deviations: () => of([]),
      nonConformities: () =>
        of([
          nc(),
          nc({ id: 'nc2', code: 'NC-002', overdue: true, overduePhases: ['containment'] }),
          nc({ id: 'nc3', code: 'NC-003', status: 'VERIFIED', closable: true }),
        ]),
    });

    store.load();

    expect(store.overdueNcs().map(n => n.code)).toEqual(['NC-002']);
    expect(store.closableNcs().map(n => n.code)).toEqual(['NC-003']);
  });

  it('avisa que a verificação ineficaz continua o tratamento em vez de encerrar', () => {
    const toast = { success: vi.fn(), error: vi.fn() };
    const store = setup(
      {
        plans: () => of([]),
        deviations: () => of([]),
        nonConformities: () => of([]),
        verify: () => of(nc({ status: 'INVESTIGATED' })),
      },
      toast,
    );

    store.verify('nc1', false, 'o lote seguinte repetiu');

    expect(toast.success.mock.calls[0][0]).toContain('planeje uma ação nova');
  });

  it('guarda a recusa de fase separada do erro genérico', () => {
    const store = setup({
      plans: () => of([]),
      deviations: () => of([]),
      nonConformities: () => of([]),
      investigate: () =>
        throwError(() => ({
          status: 409,
          code: 'nc_phase_out_of_order',
            nonConformity: { code: 'NC-001', status: 'OPEN', attempted: 'investigação' }
        })),
    });

    store.investigate('nc1', 'causa', '5 porquês');

    expect(store.phaseRefusal()?.attempted).toBe('investigação');
    expect(store.ncError()).toBeNull();
  });

  it('reporta erro de carregamento sem apagar a tela', () => {
    const store = setup({
      plans: () => throwError(() => ({ status: 500 })),
      deviations: () => of([]),
      nonConformities: () => of([]),
    });

    store.load();

    expect(store.error()).toBe('Não foi possível carregar os planos de controle.');
    expect(store.loading()).toBe(false);
  });
});
