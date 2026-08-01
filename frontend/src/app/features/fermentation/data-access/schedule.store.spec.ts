import { TestBed } from '@angular/core/testing';
import { of, throwError } from 'rxjs';
import { describe, expect, it, vi } from 'vitest';
import { ToastService } from '../../../core/notifications/toast.service';
import { FermentationSchedule, ScheduleStep } from '../domain/schedule.model';
import { ReadingsApi } from './readings.api';
import { ScheduleApi } from './schedule.api';
import { ScheduleStore } from './schedule.store';

function step(overrides: Partial<ScheduleStep> = {}): ScheduleStep {
  return {
    id: 's1', sequence: 1, name: 'Primária', action: 'REST', condition: 'MANUAL', conditionDays: null,
    targetGravity: null, plannedStart: '2026-08-01T08:00:00Z', plannedEnd: '2026-08-06T08:00:00Z',
    toleranceHours: 12, responsibleUserId: 'u1', dependsOnPrevious: false, status: 'PLANNED',
    executedAt: null, deviationHours: 0, justification: null, ...overrides,
  };
}

function schedule(steps: ScheduleStep[]): FermentationSchedule {
  return { id: 'sch1', batchId: 'b1', profileId: 'p1', profileVersion: 1, steps };
}

function setup(api: Partial<ScheduleApi>, shared: Partial<ReadingsApi> = {},
    toast = { success: vi.fn(), error: vi.fn() }) {
  TestBed.configureTestingModule({
    providers: [
      ScheduleStore,
      { provide: ScheduleApi, useValue: api },
      { provide: ReadingsApi, useValue: shared },
      { provide: ToastService, useValue: toast },
    ],
  });
  return { store: TestBed.inject(ScheduleStore), toast };
}

describe('ScheduleStore', () => {
  it('lote sem agenda não é erro: é o estado inicial', () => {
    const { store } = setup({ get: () => throwError(() => ({ status: 400 })) });
    store.select('b1');
    expect(store.planned()).toBe(false);
    expect(store.error()).toBeNull();
  });

  it('expõe as etapas carregadas', () => {
    const get = vi.fn(() => of(schedule([step(), step({ id: 's2', sequence: 2, status: 'DONE' })])));
    const { store } = setup({ get });
    store.select('b1');
    expect(store.planned()).toBe(true);
    expect(store.steps().length).toBe(2);
    expect(store.pendingSteps().map(s => s.id)).toEqual(['s1']);
  });

  it('só oferece perfis publicados para planejar', () => {
    const profiles = vi.fn(() => of([
      { id: 'p1', code: 'A', name: 'A', version: 1, status: 'DRAFT', stages: [],
        stability: { windowHours: 48, minReadings: 3, toleranceSg: 0.002 } },
      { id: 'p2', code: 'A', name: 'A', version: 2, status: 'PUBLISHED', stages: [],
        stability: { windowHours: 48, minReadings: 3, toleranceSg: 0.002 } },
    ]));
    const { store } = setup({}, { profiles });
    store.loadProfiles();
    expect(store.profiles().map(p => p.id)).toEqual(['p2']);
  });

  it('guarda a prévia sem gravar e só aplica ao confirmar', () => {
    const preview = { deltaHours: 24, changes: [{ stepId: 's1', sequence: 1, name: 'Primária',
      fromStart: 'a', toStart: 'b', fromEnd: 'c', toEnd: 'd' }], blocked: [] };
    const reschedule = vi.fn(() => of(preview));
    const { store } = setup({ reschedule, get: () => of(schedule([step()])) });
    store.select('b1');

    store.previewReschedule('s1', '2026-08-02T08:00:00Z');
    expect(reschedule).toHaveBeenLastCalledWith('b1', 's1', '2026-08-02T08:00:00Z', false);
    expect(store.preview()).toEqual(preview);

    store.confirmReschedule();
    expect(reschedule).toHaveBeenLastCalledWith('b1', 's1', '2026-08-02T08:00:00Z', true);
    expect(store.preview()).toBeNull();
  });

  it('não confirma replanejamento sem prévia pendente', () => {
    const reschedule = vi.fn(() => of({ deltaHours: 0, changes: [], blocked: [] }));
    const { store } = setup({ reschedule, get: () => of(schedule([step()])) });
    store.select('b1');
    store.confirmReschedule();
    expect(reschedule).not.toHaveBeenCalled();
  });

  it('explica que etapa executada não é replanejada', () => {
    const reschedule = vi.fn(() => throwError(() => ({ status: 409 })));
    const { store, toast } = setup({ reschedule, get: () => of(schedule([step()])) });
    store.select('b1');
    store.previewReschedule('s1', '2026-08-02T08:00:00Z');
    expect(toast.error).toHaveBeenCalledWith(expect.stringContaining('executada'));
  });

  it('explica a justificativa obrigatória fora da tolerância', () => {
    const execute = vi.fn(() => throwError(() => ({ status: 400 })));
    const { store, toast } = setup({ execute, get: () => of(schedule([step()])) });
    store.select('b1');
    store.execute('s1', '2026-08-10T08:00:00Z', null);
    expect(toast.error).toHaveBeenCalledWith(expect.stringContaining('justificativa'));
  });

  it('explica conflito ao planejar agenda repetida ou com rascunho', () => {
    const plan = vi.fn(() => throwError(() => ({ status: 409 })));
    const { store } = setup({ plan, get: () => of(schedule([step()])) });
    store.select('b1');
    store.plan({ profileId: 'p1', start: '2026-08-01T08:00:00Z', responsibleUserId: 'u1',
      defaultDurationDays: 3, toleranceHours: 12 });
    expect(store.actionError()).toContain('rascunho');
  });
});
