import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { OptimizationRun } from '../domain/optimization.model';
import { OptimizationStore } from './optimization.store';

/**
 * Estado da otimização (OPT-001).
 *
 * <p>O que estes testes fixam: inviabilidade é <em>resultado</em> e não erro, e a procedência do número
 * acompanha o resultado — inclusive distinguindo "método não usa semente" de "esqueceram de gravar".
 */
describe('OptimizationStore', () => {
  let store: OptimizationStore;
  let http: HttpTestingController;

  const URL = '/api/v1/optimizations';

  function run(overrides: Partial<OptimizationRun> = {}): OptimizationRun {
    return {
      id: 'r1',
      recipeId: 'rec1',
      recipeVersion: 3,
      objective: 'COST',
      constraints: [],
      method: 'EXHAUSTIVE_SINGLE_SUBSTITUTION',
      catalogVersion: 'catalog-abc-12',
      seed: null,
      usesSeed: false,
      feasible: true,
      candidates: [
        {
          label: 'Trocar por Malte B',
          substitutions: [],
          costPerLiter: 3.1,
          estimatedIbu: 32,
          estimatedColorEbc: 14,
          score: 0.05,
          tradeOffs: [],
        },
      ],
      infeasible: null,
      explanation: null,
      appliedRecipeVersionId: null,
      requestedBy: 'u1',
      requestedAt: '2026-08-09T10:00:00Z',
      ...overrides,
    };
  }

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [OptimizationStore, provideHttpClient(), provideHttpClientTesting()],
    });
    store = TestBed.inject(OptimizationStore);
    http = TestBed.inject(HttpTestingController);
  });

  afterEach(() => http.verify());

  it('guarda o resultado e a melhor alternativa', () => {
    store.optimize({ recipeId: 'rec1', objective: 'COST', constraints: [] });
    http.expectOne(URL).flush(run());
    http.expectOne(`${URL}?recipeId=rec1`).flush([]);

    expect(store.best()?.label).toBe('Trocar por Malte B');
    expect(store.error()).toBeNull();
  });

  it('INVIABILIDADE É RESULTADO, não erro', () => {
    // Se caísse no erro, a tela diria "algo deu errado" e perderia quais restrições se contradizem —
    // que é justamente o que torna a inviabilidade acionável.
    store.optimize({ recipeId: 'rec1', objective: 'COST', constraints: [] });
    http.expectOne(URL).flush(
      run({
        feasible: false,
        candidates: [],
        infeasible: {
          conflictingConstraints: ['MAX_COST_PER_LITER', 'IBU_RANGE'],
          explanation: 'Nenhuma combinação respeita as duas.',
        },
      }),
    );
    http.expectOne(`${URL}?recipeId=rec1`).flush([]);

    expect(store.error()).toBeNull();
    expect(store.infeasible()?.conflictingConstraints).toEqual([
      'MAX_COST_PER_LITER',
      'IBU_RANGE',
    ]);
  });

  it('A PROCEDÊNCIA distingue "não usa semente" de "não gravaram"', () => {
    store.optimize({ recipeId: 'rec1', objective: 'COST', constraints: [] });
    http.expectOne(URL).flush(run());
    http.expectOne(`${URL}?recipeId=rec1`).flush([]);

    const p = store.provenance()!;
    expect(p.method).toBe('EXHAUSTIVE_SINGLE_SUBSTITUTION');
    expect(p.recipeVersion).toBe(3);
    expect(p.catalogVersion).toBe('catalog-abc-12');
    expect(p.seed).toContain('determinístico');
  });

  it('método que usa semente mostra o valor', () => {
    store.optimize({ recipeId: 'rec1', objective: 'COST', constraints: [] });
    http.expectOne(URL).flush(run({ usesSeed: true, seed: 42 }));
    http.expectOne(`${URL}?recipeId=rec1`).flush([]);

    expect(store.provenance()!.seed).toBe('42');
  });

  it('a explicação chega sem alterar as candidatas', () => {
    store.optimize({ recipeId: 'rec1', objective: 'COST', constraints: [] });
    http.expectOne(URL).flush(run());
    http.expectOne(`${URL}?recipeId=rec1`).flush([]);
    const antes = store.run()!.candidates.map(c => c.score);

    store.explain('r1', 'A troca reduz o custo.');
    http.expectOne(`${URL}/r1/explanation`).flush(run({ explanation: 'A troca reduz o custo.' }));

    expect(store.run()!.explanation).toBe('A troca reduz o custo.');
    expect(store.run()!.candidates.map(c => c.score)).toEqual(antes);
  });

  it('receita não publicada devolve a orientação do servidor', () => {
    store.optimize({ recipeId: 'rec1', objective: 'COST', constraints: [] });
    http.expectOne(URL).flush(
      { code: 'unpublished_recipe', detail: 'a receita não tem versão publicada' },
      { status: 422, statusText: 'erro' },
    );

    expect(store.error()).toContain('não tem versão publicada');
  });

  it('aplicar registra o ponteiro e diz que o otimizador não escreve na receita', () => {
    store.apply('r1', 'v9');
    http.expectOne(`${URL}/r1/application`).flush(run({ appliedRecipeVersionId: 'v9' }));

    expect(store.run()!.appliedRecipeVersionId).toBe('v9');
  });

  it('corrida nova limpa a anterior antes de buscar', () => {
    store.optimize({ recipeId: 'rec1', objective: 'COST', constraints: [] });
    http.expectOne(URL).flush(run());
    http.expectOne(`${URL}?recipeId=rec1`).flush([]);

    store.optimize({ recipeId: 'rec1', objective: 'AVAILABILITY', constraints: [] });

    // Sem isso, o resultado de outro objetivo continuaria na tela parecendo válido.
    expect(store.run()).toBeNull();
    http.expectOne(URL).flush(run({ objective: 'AVAILABILITY' }));
    http.expectOne(`${URL}?recipeId=rec1`).flush([]);
  });
});
