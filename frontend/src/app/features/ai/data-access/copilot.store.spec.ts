import { TestBed } from '@angular/core/testing';
import { of, throwError } from 'rxjs';
import { describe, expect, it, vi } from 'vitest';
import { GroundedAnswer } from '../domain/answer.model';
import { AiApi } from './ai.api';
import { CopilotStore } from './copilot.store';

function answer(over: Partial<GroundedAnswer> = {}): GroundedAnswer {
  return {
    answered: true,
    answer: 'A concentração recomendada é de 0,15% em volume.',
    citations: [
      {
        documentCode: 'FISPQ-PERAC',
        title: 'FISPQ — Ácido peracético',
        type: 'SAFETY_DATA_SHEET',
        version: 2,
        effectiveOnDate: true,
        ordinal: 0,
        quote: 'A concentração recomendada de ácido peracético é de 0,15% em volume.',
      },
    ],
    inferences: [],
    limitations: [],
    consultedSources: 3,
    discarded: [],
    ...over,
  };
}

function setup(api: Partial<AiApi> = {}): CopilotStore {
  TestBed.configureTestingModule({
    providers: [
      CopilotStore,
      { provide: AiApi, useValue: { ask: () => of(answer()), ...api } },
    ],
  });
  return TestBed.inject(CopilotStore);
}

describe('CopilotStore', () => {
  it('antes da primeira pergunta não há resposta nem desfecho', () => {
    const store = setup();

    expect(store.answer()).toBeNull();
    expect(store.grounded()).toBe(false);
    expect(store.withoutSources()).toBe(false);
    expect(store.unsupported()).toBe(false);
  });

  it('resposta sustentada é o desfecho "com fonte"', () => {
    const store = setup();

    store.ask('qual a concentração de peracético', null);

    expect(store.grounded()).toBe(true);
    expect(store.withoutSources()).toBe(false);
    expect(store.unsupported()).toBe(false);
    expect(store.answer()?.citations).toHaveLength(1);
  });

  it('sem fonte nenhuma consultada é "não há fonte" — pede indexar documento', () => {
    const store = setup({
      ask: () =>
        of(
          answer({
            answered: false,
            answer: '',
            citations: [],
            consultedSources: 0,
            limitations: ['Nenhum documento indexado trata do assunto.'],
          }),
        ),
    });

    store.ask('criogenia supercondutora', null);

    expect(store.withoutSources()).toBe(true);
    expect(store.unsupported()).toBe(false);
    expect(store.error()).toBeNull();
  });

  it('fonte consultada sem sustentação é "não sustentou" — pede outra providência', () => {
    // A distinção decide o que a tela pede: indexar um documento, ou olhar por que o modelo não sustentou.
    const store = setup({
      ask: () =>
        of(
          answer({
            answered: false,
            answer: '',
            citations: [],
            consultedSources: 4,
            limitations: ['Não foi possível confirmar as fontes.'],
            discarded: ['a frase atribuída a FISPQ-PERAC não está no trecho 0'],
          }),
        ),
    });

    store.ask('qual a concentração', null);

    expect(store.unsupported()).toBe(true);
    expect(store.withoutSources()).toBe(false);
    // Não é erro de sistema: veio 200 com o motivo.
    expect(store.error()).toBeNull();
    expect(store.answer()?.discarded).toHaveLength(1);
  });

  it('a data de vigência viaja para o servidor', () => {
    const ask = vi.fn(() => of(answer()));
    const store = setup({ ask });

    store.ask('qual a concentração', '2026-05-01');

    expect(ask).toHaveBeenCalledWith({
      question: 'qual a concentração',
      onDate: '2026-05-01',
      equipmentId: null,
    });
  });

  it('nova pergunta limpa a resposta anterior: resposta velha ao lado de pergunta nova engana', () => {
    const store = setup({
      ask: vi
        .fn()
        .mockReturnValueOnce(of(answer()))
        .mockReturnValueOnce(throwError(() => ({ status: 503, code: 'ai_provider_unavailable' }))),
    });
    store.ask('primeira', null);
    expect(store.grounded()).toBe(true);

    store.ask('segunda', null);

    expect(store.answer()).toBeNull();
    expect(store.error()).toContain('não respondeu');
  });

  it('cada recusa do gateway tem a sua explicação', () => {
    const store = setup({
      ask: () => throwError(() => ({ status: 502, code: 'ai_response_rejected' })),
    });

    store.ask('qual a concentração', null);

    expect(store.error()).toContain('fora do formato exigido');
  });

  it('sem alçada, a mensagem fala de alçada', () => {
    const store = setup({ ask: () => throwError(() => ({ status: 403 })) });

    store.ask('qual a concentração', null);

    expect(store.error()).toContain('alçada própria');
  });
});
