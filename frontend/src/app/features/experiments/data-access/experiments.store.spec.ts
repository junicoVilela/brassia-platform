import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { Experiment, PlanExperimentRequest } from '../domain/experiment.model';
import { ExperimentsStore } from './experiments.store';

/**
 * Estado dos experimentos (EXP-001).
 *
 * <p>O que estes testes fixam: a recusa por confundimento é <em>explicada</em> e nomeia os fatores, e o
 * abandonado continua visível no histórico.
 */
describe('ExperimentsStore', () => {
  let store: ExperimentsStore;
  let http: HttpTestingController;

  const URL = '/api/v1/experiments';

  function experiment(overrides: Partial<Experiment> = {}): Experiment {
    return {
      id: 'e1',
      recipeId: 'r1',
      hypothesis: 'Dry hopping a frio preserva aroma',
      controlBatchId: 'b1',
      variantBatchId: 'b2',
      isolatedVariable: {
        name: 'Temperatura',
        controlValue: '20 C',
        variantValue: '4 C',
        differs: true,
      },
      factors: [],
      plannedMeasurements: ['DENSITY'],
      sensoryPlanned: true,
      sensoryBlind: true,
      status: 'PLANNED',
      limitations: [{ code: 'SINGLE_PAIR', description: 'n=1' }],
      conclusion: null,
      plannedBy: 'u1',
      plannedAt: '2026-08-09T10:00:00Z',
      ...overrides,
    };
  }

  const request: PlanExperimentRequest = {
    recipeId: 'r1',
    hypothesis: 'h',
    controlBatchId: 'b1',
    variantBatchId: 'b2',
    factors: [{ name: 'Temperatura', controlValue: '20', variantValue: '4' }],
    plannedMeasurements: ['DENSITY'],
    sensoryPlanned: true,
    sensoryBlind: true,
  };

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [ExperimentsStore, provideHttpClient(), provideHttpClientTesting()],
    });
    store = TestBed.inject(ExperimentsStore);
    http = TestBed.inject(HttpTestingController);
  });

  afterEach(() => http.verify());

  it('separa ativos de encerrados', () => {
    store.load();
    http.expectOne(URL).flush([
      experiment({ id: 'a', status: 'RUNNING' }),
      experiment({ id: 'b', status: 'CONCLUDED' }),
      experiment({ id: 'c', status: 'ABANDONED' }),
    ]);

    expect(store.active().map(e => e.id)).toEqual(['a']);
    // Abandonado fica no histórico: esconder faria a próxima pessoa repetir a mesma tentativa.
    expect(store.closed().map(e => e.id)).toEqual(['b', 'c']);
  });

  it('CONFUNDIMENTO É EXPLICADO e nomeia os fatores', () => {
    // "Erro de validação" faria a correção provável ser remover um fator da lista, em vez de igualar os
    // dois lados — que é o oposto do que a história quer.
    store.plan(request, () => undefined);
    http.expectOne(URL).flush(
      { code: 'confounded_experiment', differingFactors: ['Temperatura', 'Levedura'] },
      { status: 422, statusText: 'erro' },
    );

    expect(store.planError()).toContain('Temperatura, Levedura');
    expect(store.planError()).toContain('Iguale um dos lados');
    expect(store.confoundedFactors()).toEqual(['Temperatura', 'Levedura']);
  });

  it('nenhum fator diferente tem mensagem própria', () => {
    // Zero e dois são erros opostos; a mesma mensagem para os dois não ajudaria em nenhum dos casos.
    store.plan(request, () => undefined);
    http.expectOne(URL).flush(
      { code: 'confounded_experiment', differingFactors: [] },
      { status: 422, statusText: 'erro' },
    );

    expect(store.planError()).toContain('idênticos');
  });

  it('par já ativo devolve a orientação do servidor', () => {
    store.plan(request, () => undefined);
    http.expectOne(URL).flush(
      { code: 'experiment_pair_already_active', detail: 'Conclua o anterior.' },
      { status: 409, statusText: 'conflito' },
    );

    expect(store.planError()).toBe('Conclua o anterior.');
  });

  it('conflito de estado manda recarregar em vez de insistir', () => {
    store.plan(request, () => undefined);
    http.expectOne(URL).flush(
      { code: 'illegal_experiment_transition', currentStatus: 'CONCLUDED' },
      { status: 409, statusText: 'conflito' },
    );

    expect(store.planError()).toContain('CONCLUDED');
    expect(store.planError()).toContain('Recarregue');
  });

  it('sucesso chama o callback e recarrega a lista da receita', () => {
    let chamado = false;
    store.plan(request, () => (chamado = true));
    http.expectOne(URL).flush(experiment());

    expect(chamado).toBe(true);
    http.expectOne(`${URL}?recipeId=r1`).flush([]);
  });

  it('a lista sem receita não manda o parâmetro', () => {
    store.load();

    http.expectOne(URL).flush([]);
    expect(store.loaded()).toBe(true);
  });
});
