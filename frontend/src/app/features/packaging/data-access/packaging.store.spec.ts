import { TestBed } from '@angular/core/testing';
import { of, throwError } from 'rxjs';
import { describe, expect, it, vi } from 'vitest';
import { ToastService } from '../../../core/notifications/toast.service';
import {
  Carbonation,
  CarbonationInput,
  CarbonationRecommendation,
  PackagingPlan,
} from '../domain/packaging-plan.model';
import { PackagingApi } from './packaging.api';
import { PackagingStore } from './packaging.store';

function plan(overrides: Partial<PackagingPlan> = {}): PackagingPlan {
  return {
    id: 'p1', code: 'ENV-001', batchId: 'b1', containerId: 'c1', containerVolumeMl: 355,
    plannedUnits: 800, plannedVolumeLiters: 284, lineEquipmentId: 'e1',
    plannedStart: '2026-08-20T09:00:00Z', plannedEnd: '2026-08-20T15:00:00Z',
    status: 'PLANNED',
    checklist: [
      { item: 'CONTAINER_INSPECTED', confirmed: false, confirmedBy: null, confirmedAt: null },
      { item: 'SEAL_TEST', confirmed: false, confirmedBy: null, confirmedAt: null },
      { item: 'GAS_SUPPLY', confirmed: false, confirmedBy: null, confirmedAt: null },
    ],
    checklistComplete: false, reservedAt: null, cancelReason: null, ...overrides,
  };
}

function primingInput(): CarbonationInput {
  return { method: 'PRIMING', targetVolumes: 2.4, referenceTempC: 20, primingSugar: 'SUCROSE' };
}

function recommendation(): CarbonationRecommendation {
  return {
    method: 'PRIMING', targetVolumes: 2.4, referenceTempC: 20, residualVolumes: 0.86, missingVolumes: 1.54,
    beerVolumeLiters: 284, primingSugar: 'SUCROSE', primingSugarGrams: 1667, pressureBar: null,
    calculationMethod: 'g = (vol_alvo − vol_residual) × V × 1,96 / rendimento', calculatorVersion: '1.0',
    assumptions: ['1 volume = 1,96 g de CO₂ por litro'], alerts: [],
  };
}

function confirmed(): Carbonation {
  return {
    method: 'PRIMING', targetVolumes: 2.4, referenceTempC: 20, residualVolumes: 0.86, missingVolumes: 1.54,
    primingSugar: 'SUCROSE', primingSugarGrams: 1667, pressureBar: null,
    calculationMethod: 'g = (vol_alvo − vol_residual) × V × 1,96 / rendimento', calculatorVersion: '1.0',
    alerts: [], confirmedBy: 'u1', confirmedAt: '2026-08-05T10:00:00Z',
  };
}

function setup(api: Partial<PackagingApi>, toast = { success: vi.fn(), error: vi.fn() }) {
  TestBed.configureTestingModule({
    providers: [
      PackagingStore,
      { provide: PackagingApi, useValue: api },
      { provide: ToastService, useValue: toast },
    ],
  });
  return { store: TestBed.inject(PackagingStore), toast };
}

describe('PackagingStore', () => {
  it('carrega planos e marca vazio', () => {
    const { store } = setup({ list: () => of([]) });

    store.load();

    expect(store.items()).toEqual([]);
    expect(store.empty()).toBe(true);
    expect(store.loading()).toBe(false);
  });

  it('reporta erro de carregamento sem perder o estado', () => {
    const { store } = setup({ list: () => throwError(() => ({ status: 500 })) });

    store.load();

    expect(store.error()).toBe('Não foi possível carregar os planos de envase.');
    expect(store.empty()).toBe(false);
  });

  it('só oferece lote em fermentação para envase', () => {
    const { store } = setup({
      list: () => of([]),
      batches: () => of([
        { id: 'b1', code: 'L-001', recipeName: 'IPA', status: 'FERMENTING' },
        { id: 'b2', code: 'L-002', recipeName: 'Pilsen', status: 'IN_PROGRESS' },
      ]),
      ingredients: () => of([
        { id: 'c1', code: 'lata', name: 'Lata 355', type: 'PACKAGING' },
        { id: 'm1', code: 'pilsen', name: 'Malte', type: 'MALT' },
      ]),
      equipment: () => of([{ id: 'e1', code: 'LN-1', name: 'Linha 1' }]),
    });

    store.loadReferences();

    expect(store.packageableBatches().map(b => b.id)).toEqual(['b1']);
    // Só embalagem entra na lista de embalagens.
    expect(store.containers().map(c => c.id)).toEqual(['c1']);
  });

  it('guarda todos os bloqueios da recusa ao lado do plano', () => {
    const blockers = [
      { code: 'checklist_pending' as const, message: 'Item do checklist pendente: SEAL_TEST.' },
      { code: 'line_not_clean' as const, message: 'A linha não tem ciclo de limpeza liberado.' },
    ];
    const { store, toast } = setup({
      list: () => of([plan()]),
      reserve: () => throwError(() => ({ status: 409, error: { code: 'packaging_blocked', blockers } })),
    });

    store.reserve('p1');

    expect(store.blockers()['p1']).toEqual(blockers);
    // Bloqueio é informação acionável na tela, não um toast que some.
    expect(toast.error).not.toHaveBeenCalled();
  });

  it('guarda a falta de embalagem separada dos bloqueios', () => {
    const shortfall = { containerId: 'c1', requested: 800, available: 300, unit: 'UNIT' };
    const { store } = setup({
      list: () => of([plan()]),
      reserve: () => throwError(() => ({
        status: 409, error: { code: 'insufficient_packaging_stock', shortfall },
      })),
    });

    store.reserve('p1');

    expect(store.shortfall()['p1']).toEqual(shortfall);
    expect(store.blockers()['p1']).toBeUndefined();
  });

  it('limpa a recusa anterior ao reservar de novo com sucesso', () => {
    const reserve = vi.fn()
      .mockReturnValueOnce(throwError(() => ({
        status: 409,
        error: { code: 'packaging_blocked', blockers: [{ code: 'line_not_clean', message: 'Linha suja.' }] },
      })))
      .mockReturnValueOnce(of({ planId: 'p1', reservedUnits: 800, unit: 'UNIT' }));
    const { store, toast } = setup({ list: () => of([plan()]), reserve });

    store.reserve('p1');
    expect(store.blockers()['p1']).toHaveLength(1);

    store.reserve('p1');
    expect(store.blockers()['p1']).toBeUndefined();
    expect(toast.success).toHaveBeenCalledWith('Embalagem reservada: 800 UNIT.');
  });

  it('explica a recusa de abrir plano fora do estado do lote', () => {
    const { store } = setup({
      list: () => of([]),
      plan: () => throwError(() => ({ status: 409 })),
    });

    store.plan({
      code: 'ENV-001', batchId: 'b1', containerId: 'c1', plannedUnits: 800, lineEquipmentId: 'e1',
      plannedStart: '2026-08-20T09:00:00Z', plannedEnd: '2026-08-20T15:00:00Z',
    });

    expect(store.actionError()).toBe('Código já usado ou o lote não está em fermentação.');
    expect(store.submitting()).toBe(false);
  });

  // --- carbonatação (PKG-002) ---

  it('a prévia não grava nada, só guarda a recomendação', () => {
    const recordSpy = vi.fn();
    const { store } = setup({
      previewCarbonation: () => of(recommendation()),
      recordCarbonation: recordSpy,
      carbonation: () => throwError(() => ({ status: 400 })),
    });

    store.preview('p1', primingInput());

    expect(store.recommendation()?.primingSugarGrams).toBe(1667);
    expect(recordSpy).not.toHaveBeenCalled();
    expect(store.calculating()).toBe(false);
  });

  it('confirma a carbonatação e recarrega a decisão gravada', () => {
    const { store, toast } = setup({
      recordCarbonation: () => of(undefined),
      carbonation: () => of(confirmed()),
    });

    store.confirmCarbonation('p1', primingInput());

    expect(toast.success).toHaveBeenCalledWith('Carbonatação confirmada e registrada no plano.');
    expect(store.carbonation()?.method).toBe('PRIMING');
  });

  it('mostra alvo e residual quando o priming causaria sobrepressão', () => {
    const carbonationDetail = { targetVolumes: 1.2, residualVolumes: 1.48 };
    const { store, toast } = setup({
      recordCarbonation: () => throwError(() => ({
        status: 409, error: { code: 'over_carbonation', carbonation: carbonationDetail },
      })),
      carbonation: () => throwError(() => ({ status: 400 })),
    });

    store.confirmCarbonation('p1', { ...primingInput(), targetVolumes: 1.2, referenceTempC: 4 });

    expect(store.overCarbonation()).toEqual(carbonationDetail);
    // Sobrepressão é informação acionável na tela, não um toast que some.
    expect(toast.error).not.toHaveBeenCalled();
  });

  it('explica a recusa quando o plano foi cancelado', () => {
    const { store } = setup({
      recordCarbonation: () => throwError(() => ({ status: 409, error: {} })),
      carbonation: () => throwError(() => ({ status: 400 })),
    });

    store.confirmCarbonation('p1', primingInput());

    expect(store.carbonationError()).toBe('O plano foi cancelado e não aceita carbonatação.');
    expect(store.overCarbonation()).toBeNull();
  });

  it('plano sem carbonatação não é erro: apenas não há decisão', () => {
    const { store, toast } = setup({ carbonation: () => throwError(() => ({ status: 400 })) });

    store.openCarbonationOf('p1');

    expect(store.carbonationPlanId()).toBe('p1');
    expect(store.carbonation()).toBeNull();
    expect(toast.error).not.toHaveBeenCalled();
  });

  it('fechar a carbonatação limpa prévia e decisão', () => {
    const { store } = setup({ carbonation: () => of(confirmed()) });

    store.openCarbonationOf('p1');
    expect(store.carbonation()).not.toBeNull();

    store.openCarbonationOf('p1');
    expect(store.carbonationPlanId()).toBeNull();
    expect(store.carbonation()).toBeNull();
    expect(store.recommendation()).toBeNull();
  });

  it('avisa que o cancelamento é definitivo', () => {
    const { store, toast } = setup({
      list: () => of([]),
      cancel: () => throwError(() => ({ status: 409 })),
    });

    store.cancel('p1', 'lote reprovado');

    expect(toast.error).toHaveBeenCalledWith('Plano já cancelado; o cancelamento é definitivo.');
  });
});
