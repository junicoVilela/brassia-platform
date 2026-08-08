import { TestBed } from '@angular/core/testing';
import { of, throwError } from 'rxjs';
import { describe, expect, it, vi } from 'vitest';
import { Assessment } from '../domain/assessment.model';
import { AiApi } from './ai.api';
import { AssessmentStore } from './assessment.store';
import { BatchOption, BatchesApi } from './batches.api';

const BATCHES: BatchOption[] = [
  { id: 'b1', code: 'LOTE-100', recipeName: 'IPA', status: 'FERMENTING' },
  { id: 'b2', code: 'LOTE-101', recipeName: 'Pilsen', status: 'IN_PROGRESS' },
];

function assessment(over: Partial<Assessment> = {}): Assessment {
  return {
    usable: true,
    summary: 'O lote transferiu 390 L dos 400 L planejados.',
    risks: [
      {
        statement: 'A perda de 2.5% merece acompanhamento.',
        severity: 'LOW',
        factRefs: ['perda_percentual'],
      },
    ],
    assumptions: [],
    facts: [
      {
        id: 'volume_planejado',
        label: 'Volume planejado',
        value: 400,
        unit: 'L',
        source: 'production',
        available: true,
      },
      {
        id: 'medicoes',
        label: 'Medições de qualidade',
        value: null,
        unit: '',
        source: 'quality',
        available: false,
      },
    ],
    discarded: [],
    ...over,
  };
}

function setup(api: Partial<AiApi> = {}): AssessmentStore {
  TestBed.configureTestingModule({
    providers: [
      AssessmentStore,
      { provide: AiApi, useValue: { assess: () => of(assessment()), ...api } },
      { provide: BatchesApi, useValue: { batches: () => of(BATCHES) } },
    ],
  });
  return TestBed.inject(AssessmentStore);
}

describe('AssessmentStore', () => {
  it('carrega os lotes disponíveis para avaliar', () => {
    const store = setup();

    store.load();

    expect(store.batches()).toHaveLength(2);
    expect(store.error()).toBeNull();
  });

  it('separa fato conhecido de fato ausente: a ausência merece leitura própria', () => {
    // "Ninguém mediu" é risco de desconhecimento; escondê-lo faria um lote não medido parecer sem problema.
    const store = setup();

    store.assess('b1');

    expect(store.knownFacts().map(f => f.id)).toEqual(['volume_planejado']);
    expect(store.absentFacts().map(f => f.id)).toEqual(['medicoes']);
  });

  it('avaliação inutilizável ainda deixa os fatos na tela', () => {
    // Os fatos são do domínio e valem independentemente do que o modelo disse.
    const store = setup({
      assess: () =>
        of(
          assessment({
            usable: false,
            summary: '',
            risks: [],
            discarded: ['a afirmação usa o número 62, que não é o valor de nenhum fato que ela cita'],
          }),
        ),
    });

    store.assess('b1');

    expect(store.unusable()).toBe(true);
    expect(store.knownFacts()).toHaveLength(1);
    expect(store.assessment()?.discarded).toHaveLength(1);
    // Não é erro de sistema: veio 200 com o motivo.
    expect(store.assessError()).toBeNull();
  });

  it('antes de avaliar não há avaliação nem desfecho', () => {
    const store = setup();

    store.load();

    expect(store.assessment()).toBeNull();
    expect(store.unusable()).toBe(false);
  });

  it('nova avaliação limpa a anterior: número velho ao lado de lote novo engana', () => {
    const store = setup({
      assess: vi
        .fn()
        .mockReturnValueOnce(of(assessment()))
        .mockReturnValueOnce(throwError(() => ({ status: 503, code: 'ai_provider_unavailable' }))),
    });
    store.assess('b1');
    expect(store.assessment()).not.toBeNull();

    store.assess('b2');

    expect(store.assessment()).toBeNull();
    expect(store.assessError()).toContain('não respondeu');
    expect(store.selectedBatch()).toBe('b2');
  });

  it('lote inexistente é explicado como lote, não como falha genérica', () => {
    const store = setup({ assess: () => throwError(() => ({ status: 404, code: 'unknown_batch' })) });

    store.assess('b1');

    expect(store.assessError()).toContain('não existe nesta cervejaria');
  });

  it('sem alçada, a mensagem distingue avaliar de perguntar', () => {
    const store = setup({ assess: () => throwError(() => ({ status: 403 })) });

    store.assess('b1');

    expect(store.assessError()).toContain('separada de perguntar');
  });

  it('orçamento esgotado é explicado como orçamento', () => {
    const store = setup({ assess: () => throwError(() => ({ status: 402, code: 'ai_budget_exceeded' })) });

    store.assess('b1');

    expect(store.assessError()).toContain('orçamento');
  });
});
