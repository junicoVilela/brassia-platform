import { TestBed } from '@angular/core/testing';
import { of, throwError } from 'rxjs';
import { describe, expect, it, vi } from 'vitest';
import { CommandProposal } from '../domain/proposal.model';
import { AiApi } from './ai.api';
import { BatchOption, BatchesApi } from './batches.api';
import { ProposalStore } from './proposal.store';

const BATCHES: BatchOption[] = [
  { id: 'b1', code: 'LOTE-100', recipeName: 'IPA', status: 'PACKAGED' },
];

function proposal(over: Partial<CommandProposal> = {}): CommandProposal {
  return {
    id: 'p1',
    action: 'CLOSE_BATCH_COST',
    label: 'Fechar o custo do lote',
    parameters: { batchId: 'b1' },
    rationale: 'O lote terminou e o custo segue derivado.',
    requiredPermission: 'costing.cost.close',
    executionRoute: '/costing/batches',
    executedOnConfirm: true,
    proposedBy: 'u-pediu',
    proposedAt: '2026-08-08T00:00:00Z',
    expiresAt: '2026-08-08T12:00:00Z',
    status: 'PENDING',
    expired: false,
    canConfirm: true,
    decidedBy: null,
    decidedAt: null,
    decisionNote: null,
    ...over,
  };
}

function setup(api: Partial<AiApi> = {}): ProposalStore {
  TestBed.configureTestingModule({
    providers: [
      ProposalStore,
      {
        provide: AiApi,
        useValue: {
          proposals: () => of([proposal()]),
          propose: () => of([proposal()]),
          accept: () => of(proposal({ status: 'ACCEPTED', decidedBy: 'u-confirmou' })),
          reject: () => of(proposal({ status: 'REJECTED', decidedBy: 'u-confirmou' })),
          ...api,
        },
      },
      { provide: BatchesApi, useValue: { batches: () => of(BATCHES) } },
    ],
  });
  return TestBed.inject(ProposalStore);
}

describe('ProposalStore', () => {
  it('separa pendente no prazo, vencida e decidida', () => {
    // Vencida não é decisão adiada, é oferta que caducou. Misturá-la com as vigentes faria a lista do
    // que decidir hoje crescer com o que não se decide mais.
    const store = setup({
      proposals: () =>
        of([
          proposal({ id: 'vigente' }),
          proposal({ id: 'vencida', expired: true, canConfirm: false }),
          proposal({ id: 'decidida', status: 'ACCEPTED', decidedBy: 'u2', canConfirm: false }),
        ]),
    });

    store.load();

    expect(store.awaiting().map(p => p.id)).toEqual(['vigente']);
    expect(store.expired().map(p => p.id)).toEqual(['vencida']);
    expect(store.decided().map(p => p.id)).toEqual(['decidida']);
  });

  it('nomeia a alçada que falta em vez de dizer apenas "sem permissão"', () => {
    // Um botão desabilitado sem nome de permissão deixa quem lê sem saber a quem pedir.
    const store = setup();

    expect(store.missingPermissionLabel(proposal())).toBe('fechar o custo do lote');
    expect(store.missingPermissionLabel(proposal({ requiredPermission: 'x.y.z' }))).toBe('x.y.z');
  });

  it('sem alçada do comando, o erro explica que pedir a proposta não dá esse direito', () => {
    const store = setup({ accept: () => throwError(() => ({ status: 403 })) });

    store.accept('p1');

    expect(store.actionError()).toContain('pedir a proposta não dá esse direito');
  });

  it('proposta já decidida por outra pessoa recarrega a lista em vez de insistir', () => {
    // O estado na tela está velho, e remendar em memória mostraria o que a tela imaginou.
    const proposals = vi.fn().mockReturnValue(of([proposal({ status: 'ACCEPTED', decidedBy: 'outro' })]));
    const store = setup({
      proposals,
      accept: () => throwError(() => ({ status: 409, code: 'proposal_not_pending' })),
    });
    store.load();

    store.accept('p1');

    expect(store.actionError()).toContain('já decidiu');
    // Uma leitura no load e outra depois do conflito.
    expect(proposals).toHaveBeenCalledTimes(2);
    expect(store.awaiting()).toHaveLength(0);
  });

  it('proposta vencida orienta a pedir outra, não a tentar de novo', () => {
    const store = setup({
      accept: () => throwError(() => ({ status: 410, code: 'proposal_expired' })),
    });

    store.accept('p1');

    expect(store.actionError()).toContain('Peça uma nova');
  });

  it('nenhuma providência proposta é dito na tela, não silêncio', () => {
    // Zero é resposta legítima; sem dizer isso, a tela pareceria não ter feito nada.
    const store = setup({ propose: () => of([]) });

    store.propose('b1');

    expect(store.lastProposed()).toBe(0);
    expect(store.actionError()).toBeNull();
  });

  it('propor recarrega a lista para que a proposta nova apareça pendente', () => {
    const proposals = vi.fn().mockReturnValue(of([proposal()]));
    const store = setup({ proposals });

    store.propose('b1');

    expect(store.lastProposed()).toBe(1);
    expect(proposals).toHaveBeenCalledTimes(1);
    expect(store.awaiting()).toHaveLength(1);
  });

  it('descartar não depende da alçada do comando: a recusa vale igual', () => {
    const store = setup({
      proposals: vi
        .fn()
        .mockReturnValueOnce(of([proposal({ canConfirm: false })]))
        .mockReturnValueOnce(of([proposal({ status: 'REJECTED', canConfirm: false, decidedBy: 'u' })])),
    });
    store.load();
    expect(store.awaiting()).toHaveLength(1);

    store.reject('p1', 'Não se aplica.');

    expect(store.actionError()).toBeNull();
    expect(store.decided()).toHaveLength(1);
  });

  it('falha ao carregar não deixa a tela sem explicação', () => {
    const store = setup({ proposals: () => throwError(() => ({ status: 503 })) });

    store.load();

    expect(store.error()).not.toBeNull();
    expect(store.loading()).toBe(false);
  });
});
