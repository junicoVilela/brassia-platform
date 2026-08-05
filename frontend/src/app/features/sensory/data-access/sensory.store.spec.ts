import { TestBed } from '@angular/core/testing';
import { of, throwError } from 'rxjs';
import { describe, expect, it, vi } from 'vitest';
import { ToastService } from '../../../core/notifications/toast.service';
import { SensorySession, SessionResults } from '../domain/sensory.model';
import { SensoryApi } from './sensory.api';
import { SensoryStore } from './sensory.store';

function session(over: Partial<SensorySession> = {}): SensorySession {
  return {
    id: 's1',
    code: 'SEN-001',
    purpose: 'Comparativo',
    scheduledFor: '2026-08-03',
    status: 'OPEN',
    statusLabel: 'Em avaliação',
    resultsAvailable: false,
    evaluationCount: 0,
    samples: [{ id: 'sm1', blindCode: '473', batchId: null, note: null }],
    openedAt: '2026-08-03T14:00:00Z',
    closedAt: null,
    ...over,
  };
}

function results(difference: number): SessionResults {
  return {
    samples: [],
    consistency: [{ batchId: 'b1', blindCodes: ['473', '218'], difference }],
  };
}

function setup(api: Partial<SensoryApi>, toast = { success: vi.fn(), error: vi.fn() }): SensoryStore {
  TestBed.configureTestingModule({
    providers: [
      SensoryStore,
      { provide: SensoryApi, useValue: api },
      { provide: ToastService, useValue: toast },
    ],
  });
  return TestBed.inject(SensoryStore);
}

describe('SensoryStore', () => {
  it('carrega as sessões', () => {
    const store = setup({ sessions: () => of([session()]) });

    store.load();

    expect(store.sessions()).toHaveLength(1);
    expect(store.empty()).toBe(false);
  });

  it('separa as sessões que aceitam ficha agora', () => {
    const store = setup({
      sessions: () =>
        of([
          session(),
          session({ id: 's2', code: 'SEN-002', status: 'DRAFT', statusLabel: 'Rascunho' }),
          session({ id: 's3', code: 'SEN-003', status: 'CLOSED', statusLabel: 'Encerrada' }),
        ]),
    });

    store.load();

    expect(store.openSessions().map(s => s.code)).toEqual(['SEN-001']);
  });

  it('não busca resultado de sessão que ainda não fechou', () => {
    // A tela nem tenta: a regra é do backend, mas pedir e receber 409 seria ruído inútil.
    const resultsSpy = vi.fn(() => of(results(0)));
    const store = setup({ sessions: () => of([]), results: resultsSpy });

    store.toggleSession(session({ resultsAvailable: false }));

    expect(resultsSpy).not.toHaveBeenCalled();
    expect(store.results()).toBeNull();
  });

  it('busca o resultado quando a sessão já foi encerrada', () => {
    const store = setup({ sessions: () => of([]), results: () => of(results(0)) });

    store.toggleSession(session({ status: 'CLOSED', resultsAvailable: true }));

    expect(store.results()).not.toBeNull();
  });

  it('destaca o painel que divergiu sobre o mesmo lote', () => {
    // A cerveja era a mesma; diferença alta é viés de quem prova.
    const store = setup({ sessions: () => of([]), results: () => of(results(5)) });

    store.toggleSession(session({ status: 'CLOSED', resultsAvailable: true }));

    expect(store.inconsistentBatches()).toHaveLength(1);
  });

  it('não destaca divergência pequena', () => {
    const store = setup({ sessions: () => of([]), results: () => of(results(1)) });

    store.toggleSession(session({ status: 'CLOSED', resultsAvailable: true }));

    expect(store.inconsistentBatches()).toHaveLength(0);
  });

  it('explica a recusa de ficha duplicada em vez de mostrar erro genérico', () => {
    const store = setup({
      sessions: () => of([]),
      submit: () =>
        throwError(() => ({
          status: 409,
          code: 'already_evaluated', sample: { blindCode: '473' }
        })),
    });

    store.submit('s1', { sampleId: 'sm1', scores: {}, descriptors: [], note: null });

    expect(store.actionError()).toContain('473');
    expect(store.actionError()).toContain('imutável');
  });

  it('explica a recusa de sessão fechada', () => {
    const store = setup({
      sessions: () => of([]),
      submit: () =>
        throwError(() => ({
          status: 409,
          code: 'session_not_open', session: { code: 'SEN-001', status: 'Encerrada' }
        })),
    });

    store.submit('s1', { sampleId: 'sm1', scores: {}, descriptors: [], note: null });

    expect(store.actionError()).toContain('Encerrada');
  });

  it('reporta erro de carregamento sem apagar a tela', () => {
    const store = setup({ sessions: () => throwError(() => ({ status: 500 })) });

    store.load();

    expect(store.error()).toBe('Não foi possível carregar as sessões sensoriais.');
    expect(store.loading()).toBe(false);
  });
});
