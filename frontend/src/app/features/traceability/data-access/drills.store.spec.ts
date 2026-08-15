import { TestBed } from '@angular/core/testing';
import { of, throwError } from 'rxjs';
import { describe, expect, it, vi } from 'vitest';
import { ToastService } from '../../../core/notifications/toast.service';
import { DrillReport, RecallDrill } from '../domain/drill.model';
import { LineageNode, NodeType } from '../domain/genealogy.model';
import { DrillsApi } from './drills.api';
import { DrillsStore } from './drills.store';

function node(type: NodeType, label: string): LineageNode {
  return { type, id: `${type}-${label}`, label };
}

function drill(over: Partial<RecallDrill> = {}): RecallDrill {
  return {
    id: 'd1',
    code: 'SIM-2026-0001',
    origin: node('BATCH', 'LOTE-100'),
    note: null,
    status: 'RUNNING',
    startedAt: '2026-08-05T09:00:00Z',
    finishedAt: null,
    unitsInScope: null,
    unitsLocated: null,
    locatedPercent: null,
    destinationsReached: null,
    gapsFound: null,
    summary: null,
    correctiveActions: null,
    nonConformityId: null,
    elapsedSeconds: 600,
    ...over,
  };
}

function report(over: Partial<DrillReport> = {}): DrillReport {
  return {
    drill: drill(),
    unitsInScope: 120,
    destinationsReached: 1,
    destinations: [{ reference: 's1', destination: 'Bar do Zé', contact: null, units: 120 }],
    gaps: [],
    findings: ['1 destino(s) sem contato cadastrado: …'],
    ...over,
  };
}

function setup(api: Partial<DrillsApi> = {}): DrillsStore {
  TestBed.configureTestingModule({
    providers: [
      DrillsStore,
      {
        provide: DrillsApi,
        useValue: {
          list: () => of([drill()]),
          report: () => of(report()),
          start: () => of(drill()),
          finish: () => of(undefined),
          ...api,
        },
      },
      { provide: ToastService, useValue: { success: vi.fn() } },
    ],
  });
  return TestBed.inject(DrillsStore);
}

describe('DrillsStore', () => {
  it('separa os simulados em andamento', () => {
    const store = setup({
      list: () => of([drill(), drill({ id: 'd2', status: 'FINISHED', locatedPercent: 80 })]),
    });

    store.load();

    expect(store.running().map(d => d.id)).toEqual(['d1']);
  });

  it('a média de cobertura ignora os simulados sem escopo', () => {
    const store = setup({
      list: () =>
        of([
          drill({ id: 'd1', status: 'FINISHED', locatedPercent: 100 }),
          drill({ id: 'd2', status: 'FINISHED', locatedPercent: 60 }),
          // Simulado sobre lote que nunca saiu: percentual nulo não é zero, e não entra na média.
          drill({ id: 'd3', status: 'FINISHED', locatedPercent: null }),
        ]),
    });

    store.load();

    expect(store.averageCoverage()).toBe(80);
  });

  it('sem simulado encerrado não há média a mostrar', () => {
    const store = setup();

    store.load();

    expect(store.averageCoverage()).toBeNull();
  });

  it('encerrar recarrega o relatório: os números passam a ser os congelados', () => {
    const reportCall = vi
      .fn()
      .mockReturnValueOnce(of(report()))
      .mockReturnValueOnce(
        of(
          report({
            drill: drill({ status: 'FINISHED', unitsInScope: 120, unitsLocated: 90, locatedPercent: 75 }),
          }),
        ),
      );
    const store = setup({ report: reportCall });
    store.load();
    store.select('d1');

    store.finish('d1', 90, 'duas ligações', null);

    expect(reportCall).toHaveBeenCalledTimes(2);
    expect(store.report()?.drill.locatedPercent).toBe(75);
  });

  it('traduz a recusa de localizar mais do que saiu', () => {
    const store = setup({ finish: () => throwError(() => ({ status: 400 })) });

    store.finish('d1', 5000, 'achei demais', null);

    expect(store.actionError()).toContain('não dá para localizar mais do que saiu');
  });
});
