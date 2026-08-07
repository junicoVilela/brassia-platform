import { TestBed } from '@angular/core/testing';
import { of, throwError } from 'rxjs';
import { describe, expect, it, vi } from 'vitest';
import { ToastService } from '../../../core/notifications/toast.service';
import { BatchReport } from '../domain/batch-report.model';
import { BatchOption, ReportingApi } from './reporting.api';

import { ReportingStore } from './reporting.store';

function report(over: Partial<BatchReport> = {}): BatchReport {
  return {
    batchId: 'b1',
    batchCode: 'LOTE-100',
    recipeName: 'IPA',
    recipeVersion: 2,
    status: 'COMPLETED',
    generatedAt: '2026-08-07T12:00:00Z',
    incomplete: false,
    plan: { volumeLiters: 400, materials: [{ ingredientId: 'i1', quantity: 20, unit: 'KG' }] },
    execution: {
      transferredVolumeLiters: 390,
      transferLossesLiters: 8,
      transferred: true,
      packaged: true,
      packaging: [
        {
          planCode: 'ENV-1',
          plannedVolumeLiters: 142,
          packagedVolumeLiters: 138.45,
          rejectedVolumeLiters: 1.77,
          lossesLiters: 1.78,
        },
      ],
    },
    quality: {
      measurements: 4,
      withinSpec: 4,
      unmeasured: false,
      outOfSpec: [],
      deviations: [],
      nonConformities: [],
    },
    cost: {
      total: 195,
      costPerLiter: 0.5,
      volumeLiters: 390,
      closed: true,
      incomplete: false,
      gaps: [],
    },
    lineage: {
      origins: [{ type: 'STOCK_LOT', label: 'Malte F-1234' }],
      destinations: [],
      gaps: [],
      truncated: false,
      complete: true,
    },
    gaps: [],
    ...over,
  };
}

const BATCHES: BatchOption[] = [
  { id: 'b1', code: 'LOTE-100', recipeName: 'IPA', status: 'COMPLETED' },
];

function setup(api: Partial<ReportingApi> = {}): ReportingStore {
  TestBed.configureTestingModule({
    providers: [
      ReportingStore,
      {
        provide: ReportingApi,
        useValue: {
          batches: () => of(BATCHES),
          ofBatch: () => of(report()),
          export: () => of(report()),
          ...api,
        },
      },
      { provide: ToastService, useValue: { success: vi.fn() } },
    ],
  });
  return TestBed.inject(ReportingStore);
}

describe('ReportingStore', () => {
  it('o relatório é sempre relido: ele é derivado e muda com os fatos', () => {
    const ofBatch = vi.fn(() => of(report()));
    const store = setup({ ofBatch });

    store.select('b1');
    store.select('b1');
    store.select('b1');

    expect(ofBatch).toHaveBeenCalledTimes(2);
  });

  it('lote sem medição não conta como qualidade limpa', () => {
    const store = setup({
      ofBatch: () =>
        of(
          report({
            quality: {
              measurements: 0,
              withinSpec: 0,
              unmeasured: true,
              outOfSpec: [],
              deviations: [],
              nonConformities: [],
            },
          }),
        ),
    });

    store.select('b1');

    // Não medir e medir tudo dentro da faixa são coisas opostas.
    expect(store.qualityClean()).toBe(false);
  });

  it('medição fora da faixa também não é qualidade limpa', () => {
    const store = setup({
      ofBatch: () =>
        of(
          report({
            quality: {
              measurements: 4,
              withinSpec: 3,
              unmeasured: false,
              outOfSpec: [
                { parameter: 'pH', value: 4.9, unit: 'pH', measuredAt: '2026-08-07T10:00:00Z' },
              ],
              deviations: [],
              nonConformities: [],
            },
          }),
        ),
    });

    store.select('b1');

    expect(store.qualityClean()).toBe(false);
  });

  it('exportar passa pelo servidor, porque é a chamada que deixa o rastro', () => {
    const exportCall = vi.fn(() => of(report()));
    const store = setup({ export: exportCall });
    const click = vi.fn();
    stubDownload(click);

    store.exportReport('b1');

    expect(exportCall).toHaveBeenCalledWith('b1');
    expect(click).toHaveBeenCalledTimes(1);
    expect(store.exporting()).toBe(false);
  });

  it('exportar atualiza o documento em tela com o que o servidor devolveu', () => {
    const store = setup({ export: () => of(report({ incomplete: true, gaps: ['custo: incompleto'] })) });
    stubDownload(vi.fn());

    store.exportReport('b1');

    expect(store.selected()?.incomplete).toBe(true);
  });

  it('traduz a recusa de exportar, que é alçada à parte da de ler', () => {
    const store = setup({ export: () => throwError(() => ({ status: 403 })) });

    store.exportReport('b1');

    expect(store.error()).toContain('alçada própria');
  });

  it('traduz lote inexistente', () => {
    const store = setup({ ofBatch: () => throwError(() => ({ code: 'unknown_batch' })) });

    store.select('b1');

    expect(store.error()).toContain('não existe nesta cervejaria');
    expect(store.selected()).toBeNull();
  });
});

/** Substitui a âncora e o object URL para o download não sair do teste. */
function stubDownload(click: () => void): void {
  vi.spyOn(document, 'createElement').mockReturnValue({ click, href: '', download: '' } as never);
  URL.createObjectURL = vi.fn(() => 'blob:stub');
  URL.revokeObjectURL = vi.fn();
}
