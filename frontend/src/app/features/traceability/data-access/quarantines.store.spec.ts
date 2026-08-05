import { TestBed } from '@angular/core/testing';
import { of, throwError } from 'rxjs';
import { describe, expect, it, vi } from 'vitest';
import { ToastService } from '../../../core/notifications/toast.service';
import { LineageNode, NodeType } from '../domain/genealogy.model';
import { Quarantine, QuarantineDetail } from '../domain/quarantine.model';
import { QuarantinesApi } from './quarantines.api';
import { QuarantinesStore } from './quarantines.store';

function node(type: NodeType, label: string): LineageNode {
  return { type, id: `${type}-${label}`, label };
}

const LOTE = node('BATCH', 'LOTE-100');
const PLANO = node('PACKAGING_PLAN', 'ENV-1');
const ENVASE = node('PACKAGING_RUN', 'ENV-1 — 780 un');

function quarantine(over: Partial<Quarantine> = {}): Quarantine {
  return {
    id: 'q1',
    origin: LOTE,
    reason: 'desvio de pH',
    status: 'OPEN',
    openedAt: '2026-08-05T10:00:00Z',
    releasedAt: null,
    releaseJustification: null,
    ...over,
  };
}

function detail(over: Partial<QuarantineDetail> = {}): QuarantineDetail {
  return {
    quarantine: quarantine(),
    truncated: false,
    affected: [
      { node: PLANO, suspected: false },
      { node: ENVASE, suspected: true },
    ],
    ...over,
  };
}

function setup(api: Partial<QuarantinesApi> = {}): QuarantinesStore {
  TestBed.configureTestingModule({
    providers: [
      QuarantinesStore,
      {
        provide: QuarantinesApi,
        useValue: {
          list: () => of([quarantine()]),
          detail: () => of(detail()),
          open: () => of(quarantine()),
          release: () => of(undefined),
          ...api,
        },
      },
      { provide: ToastService, useValue: { success: vi.fn() } },
    ],
  });
  return TestBed.inject(QuarantinesStore);
}

describe('QuarantinesStore', () => {
  it('abre a lista pelas quarentenas abertas — é a pergunta do dia a dia', () => {
    const list = vi.fn(() => of([quarantine()]));
    const store = setup({ list });

    store.load();

    expect(list).toHaveBeenCalledWith(true);
    expect(store.openCount()).toBe(1);
  });

  it('conta separadamente o que está parado por suspeita', () => {
    const store = setup();
    store.load();

    store.select('q1');

    expect(store.detail()?.affected).toHaveLength(2);
    // Um dos dois chegou por reserva: bloqueia igual, e não é a mesma afirmação.
    expect(store.suspectedCount()).toBe(1);
  });

  it('selecionar a mesma quarentena de novo fecha o alcance', () => {
    const store = setup();
    store.load();
    store.select('q1');

    store.select('q1');

    expect(store.detail()).toBeNull();
  });

  it('o alcance é sempre buscado no servidor, nunca montado a partir da lista', () => {
    const detailCall = vi.fn(() => of(detail()));
    const store = setup({ detail: detailCall });
    store.load();

    store.select('q1');
    store.select('q1');
    store.select('q1');

    // Duas aberturas, duas buscas: uma cópia local envelheceria como a do backend envelheceria.
    expect(detailCall).toHaveBeenCalledTimes(2);
  });

  it('traduz a recusa de segunda quarentena do mesmo nó', () => {
    const store = setup({
      // O interceptor desembrulha o Problem Details: `code` chega no primeiro nível.
      open: () => throwError(() => ({ status: 409, code: 'already_quarantined' })),
    });

    store.open('BATCH', 'x', 'motivo');

    expect(store.actionError()).toContain('já está em quarentena');
  });

  it('traduz a recusa de alçada na liberação', () => {
    const store = setup({ release: () => throwError(() => ({ status: 403 })) });

    store.release('q1', 'contraprova negativa');

    expect(store.actionError()).toContain('alçada própria');
  });

  it('liberar recarrega a lista e fecha o alcance aberto', () => {
    const list = vi.fn(() => of([quarantine({ status: 'RELEASED' })]));
    const store = setup({ list });
    store.load();
    store.select('q1');

    store.release('q1', 'contraprova negativa');

    expect(list).toHaveBeenCalledTimes(2);
    expect(store.detail()).toBeNull();
    expect(store.saving()).toBeNull();
  });
});
