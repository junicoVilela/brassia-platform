import { TestBed } from '@angular/core/testing';
import { of, throwError } from 'rxjs';
import { describe, expect, it, vi } from 'vitest';
import { ToastService } from '../../../core/notifications/toast.service';
import { FermentationReading, FgStability } from '../domain/reading.model';
import { ReadingsApi } from './readings.api';
import { ReadingsStore } from './readings.store';

function reading(overrides: Partial<FermentationReading> = {}): FermentationReading {
  return {
    id: 'r1', batchId: 'b1', kind: 'DENSITY', source: 'MANUAL', value: 1.048, unit: 'SG',
    measuredAt: '2026-07-31T10:00:00Z', valid: true, invalidReason: null, ...overrides,
  };
}

function setup(api: Partial<ReadingsApi>, toast = { success: vi.fn(), error: vi.fn() }) {
  TestBed.configureTestingModule({
    providers: [
      ReadingsStore,
      { provide: ReadingsApi, useValue: api },
      { provide: ToastService, useValue: toast },
    ],
  });
  return { store: TestBed.inject(ReadingsStore), toast };
}

describe('ReadingsStore', () => {
  it('seleciona o primeiro lote e já carrega sua série', () => {
    const list = vi.fn(() => of([reading()]));
    const { store } = setup({ batches: () => of([{ id: 'b1', code: 'OP-1', recipeName: 'IPA' }]), list });
    store.loadBatches();
    expect(store.batchId()).toBe('b1');
    expect(list).toHaveBeenCalledWith('b1', 'DENSITY');
    expect(store.items().length).toBe(1);
  });

  it('recarrega ao trocar a grandeza', () => {
    const list = vi.fn(() => of([]));
    const { store } = setup({ batches: () => of([{ id: 'b1', code: 'OP-1', recipeName: 'IPA' }]), list });
    store.loadBatches();
    store.selectKind('TEMPERATURE');
    expect(list).toHaveBeenLastCalledWith('b1', 'TEMPERATURE');
    expect(store.empty()).toBe(true);
  });

  it('não busca leituras sem lote selecionado', () => {
    const list = vi.fn(() => of([]));
    const { store } = setup({ list });
    store.load();
    expect(list).not.toHaveBeenCalled();
  });

  it('conta as leituras sinalizadas', () => {
    const list = vi.fn(() => of([
      reading(),
      reading({ id: 'r2', valid: false, invalidReason: 'fora da faixa', source: 'SENSOR' }),
    ]));
    const { store } = setup({ batches: () => of([{ id: 'b1', code: 'OP-1', recipeName: 'IPA' }]), list });
    store.loadBatches();
    expect(store.invalidCount()).toBe(1);
  });

  it('avisa quando a leitura gravada foi sinalizada como implausível', () => {
    const record = vi.fn(() => of({ id: 'r9', valid: false, invalidReason: 'fora da faixa plausível' }));
    const { store, toast } = setup({ record, list: () => of([]) });
    store.select('b1');
    store.record({ batchId: 'b1', kind: 'TEMPERATURE', source: 'SENSOR', value: 150, unit: 'C',
      measuredAt: '2026-07-31T10:00:00Z' });
    expect(toast.error).toHaveBeenCalled();
    expect(toast.success).not.toHaveBeenCalled();
    expect(store.actionError()).toBeNull();
  });

  it('só oferece perfis publicados para reger o parecer', () => {
    const profiles = vi.fn(() => of([
      { id: 'p1', code: 'ALE', name: 'Ale', version: 1, status: 'DRAFT', stages: [],
        stability: { windowHours: 48, minReadings: 3, toleranceSg: 0.002 } },
      { id: 'p2', code: 'ALE', name: 'Ale', version: 2, status: 'PUBLISHED', stages: [],
        stability: { windowHours: 48, minReadings: 3, toleranceSg: 0.002 } },
    ]));
    const { store } = setup({ profiles });
    store.loadProfiles();
    expect(store.profiles().map(p => p.id)).toEqual(['p2']);
    expect(store.profileId()).toBe('p2');
  });

  it('avalia a estabilidade de FG e guarda o parecer', () => {
    const parecer: FgStability = {
      stable: false, verdict: 'WINDOW_NOT_COVERED', policy: { windowHours: 48, minReadings: 3, toleranceSg: 0.002 },
      spanHours: 4, amplitudeSg: 0.0001, readings: [],
    };
    const fgStability = vi.fn(() => of(parecer));
    const { store } = setup({ fgStability, list: () => of([]) });
    store.select('b1');
    store.selectProfile('p1');
    store.evaluateStability();
    expect(fgStability).toHaveBeenCalledWith('b1', 'p1');
    expect(store.stability()?.verdict).toBe('WINDOW_NOT_COVERED');
  });

  it('explica que rascunho não pode reger a avaliação', () => {
    const fgStability = vi.fn(() => throwError(() => ({ status: 409 })));
    const { store } = setup({ fgStability, list: () => of([]) });
    store.select('b1');
    store.selectProfile('p1');
    store.evaluateStability();
    expect(store.stabilityError()).toContain('publique');
  });

  it('não avalia sem lote ou sem perfil', () => {
    const fgStability = vi.fn(() => of(null as unknown as FgStability));
    const { store } = setup({ fgStability, list: () => of([]) });
    store.select('b1');
    store.evaluateStability();
    expect(fgStability).not.toHaveBeenCalled();
  });

  it('reporta erro de carga', () => {
    const { store } = setup({ list: () => throwError(() => new Error('boom')) });
    store.select('b1');
    expect(store.error()).not.toBeNull();
  });
});
