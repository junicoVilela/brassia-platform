import { TestBed } from '@angular/core/testing';
import { of, throwError } from 'rxjs';
import { describe, expect, it, vi } from 'vitest';
import { ToastService } from '../../../core/notifications/toast.service';
import { FermentationReading } from '../domain/reading.model';
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

  it('reporta erro de carga', () => {
    const { store } = setup({ list: () => throwError(() => new Error('boom')) });
    store.select('b1');
    expect(store.error()).not.toBeNull();
  });
});
