import { TestBed } from '@angular/core/testing';
import { OfflineQueueStore } from './offline-queue.store';

/**
 * A fila de apontamentos offline (PWA-002).
 *
 * <p>O que estes testes fixam é o critério da história: **conflito não sobrescreve silenciosamente**. Um
 * item em conflito sai do ciclo automático e espera decisão — nem some, nem é aplicado à força.
 */
describe('OfflineQueueStore', () => {
  const ANA = 'user-ana';
  const BRUNO = 'user-bruno';
  const CERVEJARIA_A = 'brewery-a';
  const CERVEJARIA_B = 'brewery-b';

  let store: OfflineQueueStore;

  const payload = { kind: 'TEMPERATURE', value: 66, unit: 'C', source: 'MANUAL' };

  beforeEach(() => {
    localStorage.clear();
    TestBed.configureTestingModule({});
    store = TestBed.inject(OfflineQueueStore);
  });

  afterEach(() => localStorage.clear());

  it('enfileira gerando uma identidade por apontamento', () => {
    const a = store.enqueue(ANA, CERVEJARIA_A, 'b1', 'LOTE-001', payload);
    const b = store.enqueue(ANA, CERVEJARIA_A, 'b1', 'LOTE-001', payload);

    // Mesmo conteúdo, apontamentos diferentes: duas leituras iguais em sequência são dois fatos.
    expect(a.clientRequestId).not.toBe(b.clientRequestId);
    expect(store.pending().length).toBe(2);
  });

  it('a fila sobrevive ao recarregar da mesma pessoa e cervejaria', () => {
    store.enqueue(ANA, CERVEJARIA_A, 'b1', 'LOTE-001', payload);

    const outra = TestBed.inject(OfflineQueueStore);
    outra.load(ANA, CERVEJARIA_A);

    expect(outra.pending().length).toBe(1);
  });

  it('fila de OUTRO usuário é apagada, não ignorada', () => {
    // Deixá-la no disco guardaria o apontamento de alguém num aparelho que trocou de mão.
    store.enqueue(ANA, CERVEJARIA_A, 'b1', 'LOTE-001', payload);

    store.load(BRUNO, CERVEJARIA_A);

    expect(store.entries()).toEqual([]);
    expect(localStorage.getItem('brassia.offline.queue')).toBeNull();
  });

  it('fila de OUTRA cervejaria é apagada', () => {
    store.enqueue(ANA, CERVEJARIA_A, 'b1', 'LOTE-001', payload);

    store.load(ANA, CERVEJARIA_B);

    expect(store.entries()).toEqual([]);
  });

  it('confirmado sai da fila', () => {
    const entry = store.enqueue(ANA, CERVEJARIA_A, 'b1', 'LOTE-001', payload);

    store.acknowledge(ANA, CERVEJARIA_A, entry.clientRequestId);

    expect(store.entries()).toEqual([]);
  });

  it('falha transitória conta a tentativa e MANTÉM na fila', () => {
    // É o que faz a garantia ser "ao menos uma vez": desistir na primeira falha perderia o apontamento de
    // quem estava sem rede, que é a única razão de a fila existir.
    const entry = store.enqueue(ANA, CERVEJARIA_A, 'b1', 'LOTE-001', payload);

    store.registerAttempt(ANA, CERVEJARIA_A, entry.clientRequestId);
    store.registerAttempt(ANA, CERVEJARIA_A, entry.clientRequestId);

    expect(store.pending().length).toBe(1);
    expect(store.pending()[0].attempts).toBe(2);
  });

  it('CONFLITO sai do ciclo automático e espera decisão — não some nem é reenviado', () => {
    const entry = store.enqueue(ANA, CERVEJARIA_A, 'b1', 'LOTE-001', payload);

    store.markConflict(ANA, CERVEJARIA_A, entry.clientRequestId, 'a etapa já foi concluída');

    expect(store.hasConflicts()).toBe(true);
    expect(store.conflicts()[0].conflict).toBe('a etapa já foi concluída');
    // Sai do que a fila tenta sozinha…
    expect(store.pending().length).toBe(0);
    // …mas continua existindo: descartar em silêncio perderia o apontamento.
    expect(store.entries().length).toBe(1);
  });

  it('o conflito só sai quando quem registrou decide descartar', () => {
    const entry = store.enqueue(ANA, CERVEJARIA_A, 'b1', 'LOTE-001', payload);
    store.markConflict(ANA, CERVEJARIA_A, entry.clientRequestId, 'lote encerrado');

    store.discard(ANA, CERVEJARIA_A, entry.clientRequestId);

    expect(store.entries()).toEqual([]);
  });

  it('o conteúdo do apontamento é congelado no registro', () => {
    // O apontamento é o que foi medido, não o que vale agora. Recalcular no envio registraria outra coisa.
    const entry = store.enqueue(ANA, CERVEJARIA_A, 'b1', 'LOTE-001', payload);

    store.registerAttempt(ANA, CERVEJARIA_A, entry.clientRequestId);

    expect(store.pending()[0].payload).toEqual(payload);
    expect(store.pending()[0].recordedAt).toBe(entry.recordedAt);
  });

  it('a fila persiste no aparelho carimbada com dono e cervejaria', () => {
    store.enqueue(ANA, CERVEJARIA_A, 'b1', 'LOTE-001', payload);

    const gravado = localStorage.getItem('brassia.offline.queue') ?? '';

    expect(gravado).toContain(ANA);
    expect(gravado).toContain(CERVEJARIA_A);
  });

  it('conteúdo corrompido é apagado em vez de usado', () => {
    localStorage.setItem('brassia.offline.queue', 'não é json');

    store.load(ANA, CERVEJARIA_A);

    expect(store.entries()).toEqual([]);
    expect(localStorage.getItem('brassia.offline.queue')).toBeNull();
  });

  it('clear esvazia o aparelho — é o que o logout precisa fazer', () => {
    store.enqueue(ANA, CERVEJARIA_A, 'b1', 'LOTE-001', payload);

    store.clear();

    expect(store.entries()).toEqual([]);
    expect(localStorage.getItem('brassia.offline.queue')).toBeNull();
  });
});
