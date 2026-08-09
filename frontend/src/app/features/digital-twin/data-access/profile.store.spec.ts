import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { LearnedProfile, ProfileEstimate } from '../domain/profile.model';
import { ProfileStore } from './profile.store';

/**
 * Estado do perfil aprendido (DTW-001).
 *
 * <p>O que estes testes fixam na interface: "nunca analisada" é um estado distinto de "carregando", e as
 * estimativas que não deram continuam visíveis — ausência declarada é informação.
 */
describe('ProfileStore', () => {
  let store: ProfileStore;
  let http: HttpTestingController;

  const RECEITA = 'r1';

  function estimate(overrides: Partial<ProfileEstimate>): ProfileEstimate {
    return {
      metric: 'VOLUME_YIELD_PERCENT',
      label: 'Rendimento de volume (%)',
      mean: 92,
      standardDeviation: 2,
      lowerBound: 90,
      upperBound: 94,
      sampleSize: 3,
      confidence: 'LOW',
      usable: true,
      ...overrides,
    };
  }

  function profile(overrides: Partial<LearnedProfile> = {}): LearnedProfile {
    return {
      id: 'p1',
      recipeId: RECEITA,
      version: 1,
      estimates: [estimate({})],
      observedBatchIds: ['b1', 'b2', 'b3'],
      computedAt: '2026-08-09T10:00:00Z',
      hasAnyUsableEstimate: true,
      ...overrides,
    };
  }

  /** `load()` dispara duas chamadas. */
  function flushLoad(latest: LearnedProfile | null, history: LearnedProfile[] = []): void {
    http.expectOne(`/api/v1/digital-twin/profiles/${RECEITA}`).flush(latest);
    http.expectOne(`/api/v1/digital-twin/profiles/${RECEITA}/history`).flush(history);
  }

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [ProfileStore, provideHttpClient(), provideHttpClientTesting()],
    });
    store = TestBed.inject(ProfileStore);
    http = TestBed.inject(HttpTestingController);
  });

  afterEach(() => http.verify());

  it('carrega o perfil vigente e o histórico', () => {
    store.load(RECEITA);
    flushLoad(profile(), [profile(), profile({ version: 0 })]);

    expect(store.profile()?.version).toBe(1);
    expect(store.history().length).toBe(2);
    expect(store.loading()).toBe(false);
  });

  it('"nunca analisada" é distinto de "carregando"', () => {
    // Misturar os dois faria a tela dizer "sem dados" enquanto ainda busca, e continuar dizendo depois.
    expect(store.neverComputed()).toBe(false);

    store.load(RECEITA);
    flushLoad(null);

    expect(store.neverComputed()).toBe(true);
    expect(store.loaded()).toBe(true);
  });

  it('as estimativas que NÃO deram continuam visíveis', () => {
    // Escondê-las faria quem lê concluir que a perda é zero em vez de que ela não foi estimada.
    store.load(RECEITA);
    flushLoad(
      profile({
        estimates: [
          estimate({}),
          estimate({
            metric: 'TRANSFER_LOSS_LITERS',
            mean: null,
            lowerBound: null,
            upperBound: null,
            sampleSize: 1,
            confidence: 'INSUFFICIENT',
            usable: false,
          }),
        ],
      }),
    );

    expect(store.usable().length).toBe(1);
    expect(store.unusable().length).toBe(1);
    expect(store.unusable()[0].confidence).toBe('INSUFFICIENT');
  });

  it('a faixa é exibida como texto; sem estimativa, um travessão', () => {
    expect(store.rangeOf(estimate({}))).toBe('90 a 94');
    expect(store.rangeOf(estimate({ usable: false, lowerBound: null, upperBound: null }))).toBe('—');
  });

  it('avisa quando nem todos os lotes pedidos foram usados', () => {
    // É a informação que evita a pergunta "por que o número não mudou?".
    store.compute(RECEITA, ['b1', 'b2', 'b3']);
    http
      .expectOne('/api/v1/digital-twin/profiles')
      .flush(profile({ observedBatchIds: ['b1', 'b2'] }));
    flushLoad(profile());

    expect(store.computeError()).toBeNull();
  });

  it('amostra vazia vira mensagem que diz o que fazer', () => {
    store.compute(RECEITA, ['b1']);
    http.expectOne('/api/v1/digital-twin/profiles').flush(
      { code: 'empty_learning_sample', detail: 'Só entram lotes desta receita já transferidos.' },
      { status: 422, statusText: 'Unprocessable Entity' },
    );

    expect(store.computeError()).toContain('já transferidos');
  });

  it('403 no cálculo explica que a alçada é de quem escolhe a amostra', () => {
    store.compute(RECEITA, ['b1']);
    http
      .expectOne('/api/v1/digital-twin/profiles')
      .flush({ detail: 'x' }, { status: 403, statusText: 'Forbidden' });

    expect(store.computeError()).toContain('escolhe a amostra');
  });

  it('falha ao carregar não deixa a tela em estado ambíguo', () => {
    store.load(RECEITA);
    http.expectOne(`/api/v1/digital-twin/profiles/${RECEITA}`).error(new ProgressEvent('erro'));
    http.expectOne(`/api/v1/digital-twin/profiles/${RECEITA}/history`).flush([]);

    expect(store.error()).toContain('Não foi possível carregar');
    expect(store.loading()).toBe(false);
  });
});
