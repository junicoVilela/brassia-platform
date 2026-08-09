import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { AuthService } from '../../../core/auth/auth.service';
import { OfflineQueueFacade } from './offline-queue.facade';

/**
 * A drenagem da fila (PWA-002).
 *
 * <p>O que estes testes fixam são os **três desfechos** de um envio, que são tratados de formas diferentes
 * de propósito: confirmado sai, transitório fica, conflito espera decisão. Confundir os dois últimos é o
 * que produz sobrescrita silenciosa ou perda de apontamento.
 */
describe('OfflineQueueFacade', () => {
  const URL = '/api/v1/production/batches/b1/measurements';
  const payload = { kind: 'TEMPERATURE', value: 66, unit: 'C', source: 'MANUAL' };

  let facade: OfflineQueueFacade;
  let http: HttpTestingController;

  function login(): void {
    const auth = TestBed.inject(AuthService);
    (auth as unknown as { userState: { set: (v: unknown) => void } }).userState.set({
      userId: 'ana',
      displayName: 'Ana',
      permissions: [],
      accessibleBreweries: [],
      activeBrewery: { id: 'cervejaria-a', code: 'CV', name: 'Cervejaria' },
    });
  }

  beforeEach(() => {
    localStorage.clear();
    TestBed.configureTestingModule({
      providers: [provideHttpClient(), provideHttpClientTesting()],
    });
    facade = TestBed.inject(OfflineQueueFacade);
    http = TestBed.inject(HttpTestingController);
    login();
  });

  afterEach(() => {
    http.verify();
    localStorage.clear();
  });

  it('a chave do apontamento viaja no corpo do envio', () => {
    // É ela que permite ao servidor reconhecer o reenvio como o mesmo fato.
    const key = facade.enqueue('b1', 'LOTE-001', payload);
    void facade.flush();

    const request = http.expectOne(URL);
    expect(request.request.body.clientRequestId).toBe(key);
    expect(request.request.body.kind).toBe('TEMPERATURE');
    request.flush({ id: 'm1', duplicate: false });
  });

  it('confirmado (201) sai da fila', async () => {
    facade.enqueue('b1', 'LOTE-001', payload);
    const flushing = facade.flush();

    http.expectOne(URL).flush({ id: 'm1', duplicate: false });
    await flushing;

    expect(facade.entries()).toEqual([]);
  });

  it('repetido (200 duplicate) também sai: para a fila, os dois são "está registrado"', async () => {
    facade.enqueue('b1', 'LOTE-001', payload);
    const flushing = facade.flush();

    http.expectOne(URL).flush({ id: 'm1', duplicate: true });
    await flushing;

    expect(facade.entries()).toEqual([]);
  });

  it('falha de rede MANTÉM na fila e conta a tentativa', async () => {
    // Desistir perderia o apontamento de quem estava sem rede, que é a razão de a fila existir.
    facade.enqueue('b1', 'LOTE-001', payload);
    const flushing = facade.flush();

    http.expectOne(URL).error(new ProgressEvent('erro'), { status: 0 });
    await flushing;

    expect(facade.pending().length).toBe(1);
    expect(facade.pending()[0].attempts).toBe(1);
    expect(facade.hasConflicts()).toBe(false);
  });

  it('erro do servidor (500) é transitório: fica na fila', async () => {
    // 5xx costuma passar. Tratá-lo como conflito jogaria na mão de quem opera uma decisão que era só
    // esperar.
    facade.enqueue('b1', 'LOTE-001', payload);
    const flushing = facade.flush();

    http.expectOne(URL).flush({}, { status: 500, statusText: 'Server Error' });
    await flushing;

    expect(facade.pending().length).toBe(1);
    expect(facade.hasConflicts()).toBe(false);
  });

  it('CONFLITO (409) sai do ciclo automático e espera decisão — não some nem é reenviado', async () => {
    facade.enqueue('b1', 'LOTE-001', payload);
    const flushing = facade.flush();

    http
      .expectOne(URL)
      .flush({ detail: 'lote não está em andamento' }, { status: 409, statusText: 'Conflict' });
    await flushing;

    expect(facade.hasConflicts()).toBe(true);
    expect(facade.conflicts()[0].conflict).toContain('lote não está em andamento');
    // Não é mais tentado sozinho…
    expect(facade.pending().length).toBe(0);
    // …e não sumiu: descartar em silêncio perderia o apontamento.
    expect(facade.entries().length).toBe(1);
  });

  it('apontamento em conflito não é reenviado numa drenagem seguinte', async () => {
    facade.enqueue('b1', 'LOTE-001', payload);
    const primeira = facade.flush();
    http.expectOne(URL).flush({ detail: 'x' }, { status: 409, statusText: 'Conflict' });
    await primeira;

    await facade.flush();

    // Nenhuma requisição nova: o `http.verify()` do afterEach reprova se houver pendência.
    expect(facade.conflicts().length).toBe(1);
  });

  it('400 é conflito, não retry: não passa a ser aceito na décima tentativa', async () => {
    facade.enqueue('b1', 'LOTE-001', payload);
    const flushing = facade.flush();

    http.expectOne(URL).flush({ detail: 'unidade incompatível' }, { status: 400, statusText: 'Bad Request' });
    await flushing;

    expect(facade.hasConflicts()).toBe(true);
  });

  it('descartar é a única forma de o conflito sair da fila', async () => {
    facade.enqueue('b1', 'LOTE-001', payload);
    const flushing = facade.flush();
    http.expectOne(URL).flush({ detail: 'x' }, { status: 409, statusText: 'Conflict' });
    await flushing;

    facade.discardConflict(facade.conflicts()[0].clientRequestId);

    expect(facade.entries()).toEqual([]);
  });

  it('a drenagem para quando a rede cai no meio, sem gastar tentativa dos demais', async () => {
    facade.enqueue('b1', 'LOTE-001', payload);
    facade.enqueue('b1', 'LOTE-001', { ...payload, value: 67 });
    const flushing = facade.flush();

    http.expectOne(URL).error(new ProgressEvent('erro'), { status: 0 });
    await flushing;

    expect(facade.pending().length).toBe(2);
    // O segundo não chegou a ser tentado.
    expect(facade.pending()[1].attempts).toBe(0);
  });

  it('sem sessão não enfileira: não haveria como carimbar de quem é', () => {
    const auth = TestBed.inject(AuthService);
    (auth as unknown as { userState: { set: (v: unknown) => void } }).userState.set(null);

    expect(facade.enqueue('b1', 'LOTE-001', payload)).toBeNull();
  });
});
