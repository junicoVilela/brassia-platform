import { TestBed } from '@angular/core/testing';
import { of, throwError } from 'rxjs';
import { describe, expect, it, vi } from 'vitest';
import { ToastService } from '../../../core/notifications/toast.service';
import { YeastHarvest, YeastRecommendation } from '../domain/yeast.model';
import { YeastApi } from './yeast.api';
import { YeastStore } from './yeast.store';

function harvest(overrides: Partial<YeastHarvest> = {}): YeastHarvest {
  return {
    id: 'h1', code: 'LV-001', strainId: 's1', sourceBatchId: 'b1', parentHarvestId: null, generation: 1,
    harvestedAt: '2026-07-31T10:00:00Z', viabilityPercent: 92.5, condition: 'Creme limpo',
    storageLocation: 'Câmara 1', storageTempC: 4, status: 'QUARANTINE', available: false,
    reviewNote: null, reviewedAt: null, pitchedBatchId: null, pitchedAt: null, ...overrides,
  };
}

function setup(api: Partial<YeastApi>, toast = { success: vi.fn(), error: vi.fn() }) {
  TestBed.configureTestingModule({
    providers: [
      YeastStore,
      { provide: YeastApi, useValue: api },
      { provide: ToastService, useValue: toast },
    ],
  });
  return { store: TestBed.inject(YeastStore), toast };
}

describe('YeastStore', () => {
  it('carrega coletas (vazio)', () => {
    const list = vi.fn(() => of([]));
    const { store } = setup({ list });
    store.load();
    expect(list).toHaveBeenCalledWith(false);
    expect(store.empty()).toBe(true);
  });

  it('só oferece coleta aprovada como mãe de outra geração', () => {
    const list = vi.fn(() => of([
      harvest(),
      harvest({ id: 'h2', code: 'LV-002', status: 'REJECTED', available: false, reviewNote: 'Contaminação' }),
      harvest({ id: 'h3', code: 'LV-003', status: 'APPROVED', available: true }),
    ]));
    const { store } = setup({ list });
    store.load();
    expect(store.parentOptions().map(h => h.id)).toEqual(['h3']);
    expect(store.pendingReview().map(h => h.id)).toEqual(['h1']);
  });

  it('recarrega ao filtrar por disponíveis', () => {
    const list = vi.fn(() => of([]));
    const { store } = setup({ list });
    store.toggleOnlyAvailable(true);
    expect(list).toHaveBeenLastCalledWith(true);
    expect(store.onlyAvailable()).toBe(true);
  });

  it('avisa a geração derivada ao registrar a coleta', () => {
    const collect = vi.fn(() => of({ id: 'h9', generation: 3 }));
    const { store, toast } = setup({ collect, list: () => of([]) });
    store.collect({
      code: 'LV-009', strainId: 's1', sourceBatchId: 'b1', parentHarvestId: 'h3',
      harvestedAt: '2026-07-31T10:00:00Z', viabilityPercent: 90, condition: 'ok',
      storageLocation: 'Câmara 1', storageTempC: 4,
    });
    expect(toast.success).toHaveBeenCalledWith(expect.stringContaining('geração 3'));
  });

  it('explica conflito de código ou mãe indisponível', () => {
    const collect = vi.fn(() => throwError(() => ({ status: 409 })));
    const { store } = setup({ collect, list: () => of([]) });
    store.collect({
      code: 'LV-001', strainId: 's1', sourceBatchId: 'b1', parentHarvestId: null,
      harvestedAt: '2026-07-31T10:00:00Z', viabilityPercent: 90, condition: 'ok',
      storageLocation: 'Câmara 1', storageTempC: 4,
    });
    expect(store.actionError()).toContain('indisponível');
  });

  it('explica que a revisão já feita é definitiva', () => {
    const review = vi.fn(() => throwError(() => ({ status: 409 })));
    const { store, toast } = setup({ review, list: () => of([]) });
    store.review('h1', true, null);
    expect(toast.error).toHaveBeenCalledWith(expect.stringContaining('definitiva'));
  });

  it('alterna a genealogia da coleta', () => {
    const genealogy = vi.fn(() => of([harvest({ id: 'h3', generation: 2 }), harvest()]));
    const { store } = setup({ genealogy, list: () => of([]) });
    store.toggleGenealogy('h3');
    expect(store.genealogy().length).toBe(2);
    store.toggleGenealogy('h3');
    expect(store.genealogyOf()).toBeNull();
    expect(store.genealogy()).toEqual([]);
  });

  it('guarda recomendações e a política aplicada', () => {
    const recommendation: YeastRecommendation = {
      harvest: harvest({ status: 'APPROVED', available: true }),
      recommended: true,
      ageDays: 3,
      factors: [{ name: 'generation', withinPolicy: true, explanation: 'Geração 1 de no máximo 10' }],
      blockers: [],
    };
    const reuse = vi.fn(() => of({
      policy: { maxGeneration: 10, maxAgeDays: 21, minViabilityPercent: 70 },
      recommendations: [recommendation],
    }));
    const { store } = setup({ reuse });
    store.recommend();
    expect(reuse).toHaveBeenCalledWith(null);
    expect(store.recommendations()).toEqual([recommendation]);
    expect(store.policy()?.maxAgeDays).toBe(21);
  });

  it('confirma o uso e recarrega coletas e recomendações', () => {
    const use = vi.fn(() => of(undefined));
    const list = vi.fn(() => of([]));
    const reuse = vi.fn(() => of({
      policy: { maxGeneration: 10, maxAgeDays: 21, minViabilityPercent: 70 }, recommendations: [],
    }));
    const { store, toast } = setup({ use, list, reuse });
    store.use('h1', 'b1');
    expect(use).toHaveBeenCalledWith('h1', 'b1');
    expect(toast.success).toHaveBeenCalledWith(expect.stringContaining('consumida'));
    expect(list).toHaveBeenCalled();
    expect(reuse).toHaveBeenCalled();
  });

  it('explica que a mesma coleta não pode ser pitchada duas vezes', () => {
    const use = vi.fn(() => throwError(() => ({ status: 409 })));
    const { store, toast } = setup({ use, list: () => of([]) });
    store.use('h1', 'b1');
    expect(toast.error).toHaveBeenCalledWith(expect.stringContaining('duas vezes'));
  });

  it('reporta falta de permissão ao salvar a política', () => {
    const savePolicy = vi.fn(() => throwError(() => ({ status: 403 })));
    const { store } = setup({ savePolicy });
    store.savePolicy({ maxGeneration: 5, maxAgeDays: 7, minViabilityPercent: 85 });
    expect(store.policyError()).toContain('permissão');
  });

  it('reporta erro de carga', () => {
    const { store } = setup({ list: () => throwError(() => new Error('boom')) });
    store.load();
    expect(store.error()).not.toBeNull();
  });
});
