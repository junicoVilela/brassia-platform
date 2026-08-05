import { TestBed } from '@angular/core/testing';
import { of, throwError } from 'rxjs';
import { describe, expect, it, vi } from 'vitest';
import { ToastService } from '../../../core/notifications/toast.service';
import { LineageNode, NodeType } from '../domain/genealogy.model';
import { Recall, RecallDossier, RecallNotification } from '../domain/recall.model';
import { RecallsApi } from './recalls.api';
import { RecallsStore } from './recalls.store';

function node(type: NodeType, label: string): LineageNode {
  return { type, id: `${type}-${label}`, label };
}

const LOTE = node('BATCH', 'LOTE-100');
const ACABADO = node('FINISHED_LOT', 'LOTE-100/1');

function recall(over: Partial<Recall> = {}): Recall {
  return {
    id: 'r1',
    code: 'REC-2026-0001',
    origin: LOTE,
    reason: 'contaminação confirmada',
    status: 'OPEN',
    openedAt: '2026-08-05T10:00:00Z',
    closedAt: null,
    closingSummary: null,
    ...over,
  };
}

function notification(over: Partial<RecallNotification> = {}): RecallNotification {
  return {
    id: 'n1',
    shipmentId: 's1',
    finishedLotCode: 'LOTE-100/1',
    destination: 'Bar do Zé',
    contact: '(11) 99999-0000',
    units: 120,
    status: 'PENDING',
    channel: null,
    note: null,
    notifiedAt: null,
    ...over,
  };
}

function dossier(over: Partial<RecallDossier> = {}): RecallDossier {
  return {
    recall: recall(),
    notifications: [notification()],
    pending: 1,
    coverage: 0,
    truncated: false,
    scope: [{ node: ACABADO, suspected: false }],
    newDestinations: [],
    gaps: [],
    ...over,
  };
}

function setup(api: Partial<RecallsApi> = {}): RecallsStore {
  TestBed.configureTestingModule({
    providers: [
      RecallsStore,
      {
        provide: RecallsApi,
        useValue: {
          list: () => of([recall()]),
          dossier: () => of(dossier()),
          open: () => of(recall()),
          notify: () => of(undefined),
          close: () => of(undefined),
          ...api,
        },
      },
      { provide: ToastService, useValue: { success: vi.fn() } },
    ],
  });
  return TestBed.inject(RecallsStore);
}

describe('RecallsStore', () => {
  it('conta os recalls abertos', () => {
    const store = setup({ list: () => of([recall(), recall({ id: 'r2', status: 'CLOSED' })]) });

    store.load();

    expect(store.openCount()).toBe(1);
  });

  it('mantém separados os destinos descobertos depois da abertura', () => {
    const store = setup({
      dossier: () =>
        of(
          dossier({
            newDestinations: [
              { shipmentId: 's2', destination: 'Mercado Central', contact: null, units: 50 },
            ],
          }),
        ),
    });
    store.load();

    store.select('r1');

    // Não entram na lista dos avisados: "avisado" e "descoberto agora" são coisas diferentes.
    expect(store.dossier()?.notifications).toHaveLength(1);
    expect(store.newDestinations()).toHaveLength(1);
  });

  it('selecionar o mesmo recall de novo fecha o dossiê', () => {
    const store = setup();
    store.load();
    store.select('r1');

    store.select('r1');

    expect(store.dossier()).toBeNull();
  });

  it('registrar comunicação recarrega o dossiê — a cobertura vem do servidor', () => {
    const dossierCall = vi.fn(() => of(dossier({ pending: 0, coverage: 100 })));
    const store = setup({ dossier: dossierCall });
    store.load();
    store.select('r1');
    expect(dossierCall).toHaveBeenCalledTimes(1);

    store.notify('r1', 'n1', 'telefone', 'falei com o gerente');

    expect(dossierCall).toHaveBeenCalledTimes(2);
    expect(store.dossier()?.coverage).toBe(100);
  });

  it('traduz a recusa de encerrar com destino pendente', () => {
    const store = setup({
      // O interceptor desembrulha o Problem Details: `code` chega no primeiro nível.
      close: () =>
        throwError(() => ({ status: 409, code: 'recall_has_pending_notifications', pending: 3 })),
    });

    store.close('r1', 'recolhido');

    expect(store.actionError()).toContain('3 destino');
  });

  it('traduz a recusa de alçada', () => {
    const store = setup({ open: () => throwError(() => ({ status: 403 })) });

    store.open('BATCH', 'x', 'motivo');

    expect(store.actionError()).toContain('alçada');
  });
});
