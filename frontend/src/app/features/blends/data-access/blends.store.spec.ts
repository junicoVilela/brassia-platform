import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { BlendOperation, SimulateBlendRequest } from '../domain/blend.model';
import { BlendsStore } from './blends.store';

/**
 * Estado do blend (BLD-001).
 *
 * <p>O que estes testes fixam: o desequilíbrio chega como aritmética — quantos litros e de que lado —, e
 * o conflito de estado manda recarregar em vez de repetir, porque repetir significaria misturar duas vezes.
 */
describe('BlendsStore', () => {
  let store: BlendsStore;
  let http: HttpTestingController;

  const URL = '/api/v1/blends';

  function operation(overrides: Partial<BlendOperation> = {}): BlendOperation {
    return {
      id: 'o1',
      kind: 'MERGE',
      inputs: [{ batchId: 'b1', liters: 400 }],
      outputs: [{ batchId: 'b2', liters: 400 }],
      inputLiters: 400,
      outputLiters: 400,
      declaredLossLiters: 0,
      reason: 'Aproveitamento de sobra',
      status: 'SIMULATED',
      results: [],
      contributesLineage: false,
      simulatedBy: 'u1',
      simulatedAt: '2026-08-09T10:00:00Z',
      approvedBy: null,
      approvedAt: null,
      executedBy: null,
      executedAt: null,
      ...overrides,
    };
  }

  const request: SimulateBlendRequest = {
    kind: 'MERGE',
    inputs: [
      { batchId: 'b1', liters: 400 },
      { batchId: 'b2', liters: 200 },
    ],
    outputs: [{ batchId: 'b3', liters: 600 }],
    results: [],
    declaredLossLiters: 0,
    reason: 'x',
  };

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [BlendsStore, provideHttpClient(), provideHttpClientTesting()],
    });
    store = TestBed.inject(BlendsStore);
    http = TestBed.inject(HttpTestingController);
  });

  afterEach(() => http.verify());

  it('separa pendentes de encerradas', () => {
    store.load();
    http.expectOne(URL).flush([
      operation({ id: 'a', status: 'SIMULATED' }),
      operation({ id: 'b', status: 'APPROVED' }),
      operation({ id: 'c', status: 'EXECUTED' }),
      operation({ id: 'd', status: 'DISCARDED' }),
    ]);

    expect(store.pending().map(o => o.id)).toEqual(['a', 'b']);
    expect(store.settled().map(o => o.id)).toEqual(['c', 'd']);
  });

  it('DESEQUILÍBRIO CHEGA COMO ARITMÉTICA, com a orientação do servidor', () => {
    // "Erro de validação" faria a correção provável ser mexer no número até passar — que é o que destrói
    // o valor do campo de perda declarada.
    store.simulate(request, () => undefined);
    http.expectOne(URL).flush(
      { code: 'unbalanced_blend', detail: 'Falta explicar 100 L', difference: 100 },
      { status: 422, statusText: 'erro' },
    );

    expect(store.simulateError()).toBe('Falta explicar 100 L');
  });

  it('lote inexistente tem mensagem própria', () => {
    store.simulate(request, () => undefined);
    http.expectOne(URL).flush(
      { code: 'unknown_blend_batch', detail: 'lote de destino não existe nesta cervejaria' },
      { status: 422, statusText: 'erro' },
    );

    expect(store.simulateError()).toContain('não existe');
  });

  it('CONFLITO DE ESTADO manda recarregar, não repetir', () => {
    // Repetir aqui significaria misturar a cerveja duas vezes.
    store.simulate(request, () => undefined);
    http.expectOne(URL).flush(
      { code: 'illegal_blend_transition', currentStatus: 'EXECUTED' },
      { status: 409, statusText: 'conflito' },
    );

    expect(store.simulateError()).toContain('EXECUTED');
    expect(store.simulateError()).toContain('não se mistura duas vezes');
  });

  it('403 explica que aprovar e executar são alçadas próprias', () => {
    store.simulate(request, () => undefined);
    http.expectOne(URL).flush({}, { status: 403, statusText: 'proibido' });

    expect(store.simulateError()).toContain('não se separam');
  });

  it('sucesso avisa que nada foi movido ainda', () => {
    // A simulação é uma proposta; dizer "pronto" faria parecer que a cerveja já mudou de tanque.
    let chamado = false;
    store.simulate(request, () => (chamado = true));
    http.expectOne(URL).flush(operation());

    expect(chamado).toBe(true);
    http.expectOne(URL).flush([]);
  });

  it('executar recarrega a lista', () => {
    store.execute('o1');
    http.expectOne(`${URL}/o1/execution`).flush(operation({ status: 'EXECUTED' }));

    http.expectOne(URL).flush([]);
  });
});
