import { TestBed } from '@angular/core/testing';
import { of, throwError } from 'rxjs';
import { describe, expect, it, vi } from 'vitest';
import { ToastService } from '../../../core/notifications/toast.service';
import {
  Carbonation,
  CarbonationInput,
  CarbonationRecommendation,
  Freshness,
  LabelPreview,
  PackagingPlan,
  PackagingRun,
  ShelfLifeRecommendation,
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

function labelPreview(): LabelPreview {
  return {
    templateCode: 'RTL-01', templateVersion: 1, printable: true,
    lines: [
      { field: 'BEER_NAME', value: 'IPA da Casa', source: 'lote L-2026-014', required: true, present: true },
      { field: 'ABV', value: '6.2', source: 'receita publicada v3', required: false, present: true },
    ],
    missingRequired: [], missingOptional: [], requiredNotDrawn: [],
  };
}

function freshness(overrides: Partial<Freshness> = {}): Freshness {
  return {
    packagedOn: '2026-08-20', dissolvedOxygenPpb: 30, totalPackageOxygenPpb: 80, headspaceOxygenPpb: 50,
    purgeMethod: 'purga com CO₂', purgeVerified: true, sealCheckMethod: 'recravação medida',
    sealCheckPassed: true, evidenceComplete: true, recommendedShelfLifeDays: 120,
    recommendedBestBefore: '2026-12-18', overrideShelfLifeDays: null, overrideBestBefore: null,
    overrideReason: null, overriddenBy: null, overriddenAt: null, extendsBeyondRecommendation: false,
    effectiveShelfLifeDays: 120, effectiveBestBefore: '2026-12-18', ...overrides,
  };
}

function recommendedShelfLife(): ShelfLifeRecommendation {
  return {
    shelfLifeDays: 120, bestBefore: '2026-12-18', totalPackageOxygenPpb: 80, matchedTierMaxTpoPpb: 100,
    withinPolicyTiers: true,
    factors: [{ name: 'tpo', trustworthy: true, explanation: 'TPO de 80 ppb dentro da faixa de até 100 ppb' }],
    caveats: [],
  };
}

function packagingRun(): PackagingRun {
  return {
    id: 'r1', batchId: 'b1', inputVolumeLiters: 284, producedUnits: 780, rejectedUnits: 12,
    packagedVolumeLiters: 276.9, rejectedVolumeLiters: 4.26, lossesLiters: 2.84, lossPercent: 1,
    containersConsumed: 792, note: null, executedAt: '2026-08-20T15:00:00Z', executedBy: 'u1',
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

  // --- rótulo (PKG-004) ---

  it('a prévia mostra a origem de cada campo', () => {
    const { store } = setup({ labelPreview: () => of(labelPreview()) });

    store.previewLabel('p1', 't1');

    expect(store.labelPreview()?.printable).toBe(true);
    expect(store.labelPreview()?.lines[0].source).toContain('lote');
  });

  it('campo obrigatório faltando bloqueia a impressão com a causa', () => {
    const blocked = { missingRequired: ['ALLERGENS' as const], requiredNotDrawn: [] };
    const { store, toast } = setup({
      printLabel: () => throwError(() => ({
        status: 409, error: { code: 'label_not_printable', label: blocked },
      })),
      labelPrints: () => of([]),
    });

    store.printLabel('p1', 't1', 800, null);

    expect(store.labelBlocked()).toEqual(blocked);
    // Campo faltando é informação acionável na tela, não um toast que some.
    expect(toast.error).not.toHaveBeenCalled();
  });

  it('distingue impressão de reimpressão na confirmação', () => {
    const { store, toast } = setup({
      printLabel: () => of({ printId: 'pr1', reprint: true, quantity: 40 }),
      labelPrints: () => of([]),
      labelPreview: () => of(labelPreview()),
    });

    store.printLabel('p1', 't1', 40, 'impressora borrou');

    expect(toast.success).toHaveBeenCalledWith('Reimpressão de 40 rótulos registrada com o motivo.');
  });

  it('sem regra regulatória a tela avisa em vez de falhar', () => {
    const { store, toast } = setup({
      labelTemplates: () => of([]),
      labelRule: () => throwError(() => ({ status: 400 })),
    });

    store.loadLabelReferences();

    expect(store.labelRule()).toBeNull();
    expect(toast.error).not.toHaveBeenCalled();
  });

  it('fechar o rótulo limpa prévia e impressões', () => {
    const { store } = setup({ labelPrints: () => of([]) });

    store.openLabelOf('p1');
    expect(store.labelPlanId()).toBe('p1');

    store.openLabelOf('p1');
    expect(store.labelPlanId()).toBeNull();
    expect(store.labelPreview()).toBeNull();
    expect(store.labelPrints()).toEqual([]);
  });

  // --- oxigênio e validade (FSL-001) ---

  it('guarda a recomendação explicada junto da medição', () => {
    const { store, toast } = setup({
      recordFreshness: () => of({ freshness: freshness(), recommendation: recommendedShelfLife() }),
    });

    store.recordFreshness('p1', {
      dissolvedOxygenPpb: 30, totalPackageOxygenPpb: 80, purgeMethod: 'CO₂', purgeVerified: true,
      sealCheckMethod: 'recravação', sealCheckPassed: true,
    });

    expect(store.recommendedShelfLife()?.shelfLifeDays).toBe(120);
    expect(store.recommendedShelfLife()?.factors).toHaveLength(1);
    expect(store.freshness()?.headspaceOxygenPpb).toBe(50);
    expect(toast.success).toHaveBeenCalledWith('Validade recomendada: 120 dias.');
  });

  it('sem política, a medição vale e a validade fica em aberto', () => {
    const { store, toast } = setup({
      recordFreshness: () => of({
        freshness: freshness({ recommendedShelfLifeDays: null, recommendedBestBefore: null,
          effectiveShelfLifeDays: null, effectiveBestBefore: null }),
        recommendation: null,
      }),
    });

    store.recordFreshness('p1', {
      dissolvedOxygenPpb: 30, totalPackageOxygenPpb: 80, purgeMethod: 'CO₂', purgeVerified: true,
      sealCheckMethod: 'recravação', sealCheckPassed: true,
    });

    expect(store.recommendedShelfLife()).toBeNull();
    expect(store.freshness()?.totalPackageOxygenPpb).toBe(80);
    expect(toast.success).toHaveBeenCalledWith(
      'Medição registrada. Configure a política de vida útil para receber a recomendação.');
  });

  it('explica que o oxigênio só é medido depois do envase', () => {
    const { store } = setup({ recordFreshness: () => throwError(() => ({ status: 409 })) });

    store.recordFreshness('p1', {
      dissolvedOxygenPpb: 30, totalPackageOxygenPpb: 80, purgeMethod: 'CO₂', purgeVerified: true,
      sealCheckMethod: 'recravação', sealCheckPassed: true,
    });

    expect(store.freshnessError()).toBe('Registre o envase antes de medir o oxigênio da embalagem.');
  });

  it('recarrega o registro após sobrepor a validade', () => {
    const overridden = freshness({ overrideShelfLifeDays: 180, overrideBestBefore: '2027-02-16',
      overrideReason: 'estoque refrigerado', extendsBeyondRecommendation: true });
    const { store, toast } = setup({
      overrideShelfLife: () => of(undefined),
      freshness: () => of(overridden),
    });

    store.overrideShelfLife('p1', 180, 'estoque refrigerado');

    expect(toast.success).toHaveBeenCalledWith('Validade sobreposta e registrada com o motivo.');
    // O recomendado continua ao lado do sobreposto.
    expect(store.freshness()?.recommendedShelfLifeDays).toBe(120);
    expect(store.freshness()?.overrideShelfLifeDays).toBe(180);
  });

  it('plano ainda não medido não é erro: apenas não há registro', () => {
    const { store, toast } = setup({ freshness: () => throwError(() => ({ status: 400 })) });

    store.openFreshnessOf('p1');

    expect(store.freshnessPlanId()).toBe('p1');
    expect(store.freshness()).toBeNull();
    expect(toast.error).not.toHaveBeenCalled();
  });

  // --- execução (PKG-003) ---

  it('registra o envase e recarrega a execução', () => {
    const { store, toast } = setup({
      list: () => of([]),
      execute: () => of({ runId: 'r1', packagedVolumeLiters: 276.9, lossesLiters: 2.84,
        containersConsumed: 792 }),
      run: () => of(packagingRun()),
    });

    store.execute('p1', { inputVolumeLiters: 284, producedUnits: 780, rejectedUnits: 12, note: null });

    expect(toast.success).toHaveBeenCalledWith('Envase registrado: 792 embalagens consumidas.');
    expect(store.run()?.lossesLiters).toBe(2.84);
    expect(store.executing()).toBe(false);
  });

  it('mostra os três números quando o balanço de volume não fecha', () => {
    const balance = { inputVolumeLiters: 280, packagedVolumeLiters: 284, rejectedVolumeLiters: 0,
      shortfallLiters: 4 };
    const { store, toast } = setup({
      list: () => of([]),
      execute: () => throwError(() => ({ status: 409, error: { code: 'volume_balance', balance } })),
      run: () => throwError(() => ({ status: 400 })),
    });

    store.execute('p1', { inputVolumeLiters: 280, producedUnits: 800, rejectedUnits: 0, note: null });

    expect(store.volumeBalance()).toEqual(balance);
    // O operador precisa dos números na tela para achar qual medida está errada.
    expect(toast.error).not.toHaveBeenCalled();
  });

  it('distingue estouro do lote de falta de embalagem', () => {
    const batchVolume = { batchVolumeLiters: 390, alreadyPackagedLiters: 355, remainingLiters: 35,
      requestedLiters: 100 };
    const { store } = setup({
      list: () => of([]),
      execute: () => throwError(() => ({ status: 409, error: { code: 'batch_volume_exceeded', batchVolume } })),
      run: () => throwError(() => ({ status: 400 })),
    });

    store.execute('p1', { inputVolumeLiters: 100, producedUnits: 280, rejectedUnits: 0, note: null });

    expect(store.batchVolumeExceeded()).toEqual(batchVolume);
    expect(store.volumeBalance()).toBeNull();
    expect(store.runShortfall()).toBeNull();
  });

  it('explica que o envase acontece uma vez só', () => {
    const { store } = setup({
      list: () => of([]),
      execute: () => throwError(() => ({ status: 409, error: {} })),
      run: () => throwError(() => ({ status: 400 })),
    });

    store.execute('p1', { inputVolumeLiters: 284, producedUnits: 780, rejectedUnits: 12, note: null });

    expect(store.runError()).toBe('Só plano reservado é executado, e o envase acontece uma vez só.');
  });

  it('plano ainda não executado não é erro: apenas não há execução', () => {
    const { store, toast } = setup({ run: () => throwError(() => ({ status: 400 })) });

    store.openRunOf('p1');

    expect(store.runPlanId()).toBe('p1');
    expect(store.run()).toBeNull();
    expect(toast.error).not.toHaveBeenCalled();
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
