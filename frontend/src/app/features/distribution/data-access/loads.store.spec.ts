import { TestBed } from '@angular/core/testing';
import { of, throwError } from 'rxjs';
import { describe, expect, it, vi } from 'vitest';
import { ToastService } from '../../../core/notifications/toast.service';
import { Load } from '../domain/load.model';
import { LoadsApi } from './loads.api';
import { LoadsStore } from './loads.store';

function carga(over: Partial<Load> = {}): Load {
  return {
    id: 'c1',
    code: 'CG-001',
    scheduledFor: '2026-08-17',
    capacityLiters: 1000,
    loadedLiters: 50,
    remainingLiters: 950,
    status: 'PLANNED',
    plannedBy: 'u1',
    releasedBy: null,
    releasedAt: null,
    driverId: 'u9',
    vehicle: 'ABC-1234',
    frozen: false,
    customerCount: 1,
    route: [],
    ...over,
  };
}

function setup(api: Partial<LoadsApi>) {
  const toast = { success: vi.fn(), error: vi.fn() };
  api.list ??= () => of([carga()]);
  api.read ??= () => of(carga());
  api.proofsOfLoad ??= () => of([]);
  api.syncConflicts ??= () => of([]);
  TestBed.configureTestingModule({
    providers: [
      LoadsStore,
      { provide: LoadsApi, useValue: api },
      { provide: ToastService, useValue: toast },
    ],
  });
  return { store: TestBed.inject(LoadsStore), toast };
}

describe('LoadsStore', () => {
  it('conta as cargas que esperam conferência de outra pessoa', () => {
    // É a fila de quem tem a alçada de liberar — e quem montou não está nela.
    const { store } = setup({
      list: () =>
        of([
          carga(),
          carga({ id: 'c2' }),
          carga({ id: 'c3', status: 'IN_ROUTE', frozen: true }),
        ]),
    } as Partial<LoadsApi>);

    store.load();

    expect(store.awaitingRelease()).toBe(2);
    expect(store.onTheRoad()).toBe(1);
  });

  it('a recusa por separação de deveres chega com a mensagem do servidor', () => {
    // 409, e não 403: não é falta de permissão, é a mesma pessoa nos dois papéis.
    const { store, toast } = setup({
      release: () =>
        throwError(() => ({
          status: 409,
          code: 'separation_of_duties',
          detail: 'Quem montou a carga não pode liberá-la.',
        })),
    } as Partial<LoadsApi>);

    store.release(carga());

    expect(toast.error).toHaveBeenCalledWith('Quem montou a carga não pode liberá-la.');
  });

  it('o motivo de o keg não poder sair vira a frase de quem opera', () => {
    // Keg vazio se enche, lote não liberado se cobra da qualidade, quarentena não se resolve hoje.
    const { store, toast } = setup({
      loadContainer: () =>
        throwError(() => ({
          status: 409,
          code: 'container_not_shippable', detail: 'recusado', reasonCode: 'not_released',
        })),
    } as Partial<LoadsApi>);

    store.loadContainer(carga(), 's1', 'k1');

    expect(toast.error).toHaveBeenCalledWith(
      'A qualidade ainda não liberou o lote. Cobre a liberação antes de despachar.',
    );
  });

  it('reabrir avisa que a conferência anterior caiu', () => {
    // Senão o operador reabre achando que só destravou a edição.
    const { store, toast } = setup({ reopen: () => of(undefined) } as Partial<LoadsApi>);

    store.reopen(carga({ status: 'RELEASED', frozen: true }));

    expect(toast.success).toHaveBeenCalledWith('Carga reaberta. A conferência anterior foi desfeita.');
  });

  it('liberar relê a carga, porque "congelada" é composto no servidor', () => {
    // Uma cópia em cache mostraria botões de editar numa carga que acabou de ser conferida.
    const read = vi
      .fn()
      .mockReturnValueOnce(of(carga({ status: 'RELEASED', frozen: true, releasedBy: 'u2' })));
    const { store } = setup({ read, release: () => of(undefined) } as Partial<LoadsApi>);

    store.release(carga());

    expect(store.selected()?.frozen).toBe(true);
    expect(store.selected()?.releasedBy).toBe('u2');
  });

  it('a correção é anunciada como registro novo, e nunca como apagamento', () => {
    // Dizer "entrega corrigida" faria a tela prometer um apagamento que não houve: a original continua
    // de pé, e é isso que separa uma correção de um encobrimento.
    const { store, toast } = setup({ correctProof: () => of({ id: 'p2' }) } as Partial<LoadsApi>);

    store.correctProof(carga(), 's1', 'PARTIAL', [], [], 'só um keg desceu');

    expect(toast.success).toHaveBeenCalledWith(
      'Correção registrada. O registro anterior continua no histórico.',
    );
  });

  it('a parada já registrada some da fila, e a correção não conta como registro novo', () => {
    // A tela esconde o botão em vez de deixar o 409 explicar — e a correção não pode fazer a parada
    // parecer registrada duas vezes.
    const { store } = setup({
      proofsOfLoad: () =>
        of([
          {
            id: 'p1',
            stopId: 's1',
            outcome: 'DELIVERED',
            occurredAt: '2026-08-17T10:00:00Z',
            recordedBy: 'u1',
            delivered: ['k1'],
            collected: [],
            note: null,
            outsideWindow: false,
            mediaKind: null,
            mediaPurpose: null,
            consentedByName: null,
            latitude: null,
            longitude: null,
            correctsProofId: null,
          },
          {
            id: 'p2',
            stopId: 's1',
            outcome: 'PARTIAL',
            occurredAt: '2026-08-17T12:00:00Z',
            recordedBy: 'u1',
            delivered: [],
            collected: [],
            note: 'marquei errado',
            outsideWindow: false,
            mediaKind: null,
            mediaPurpose: null,
            consentedByName: null,
            latitude: null,
            longitude: null,
            correctsProofId: 'p1',
          },
        ]),
    } as Partial<LoadsApi>);

    store.open(carga());

    expect(store.proofs()).toHaveLength(2);
    expect(store.recordedStops().has('s1')).toBe(true);
    expect(store.recordedStops().size).toBe(1);
  });

  it('a carga que não saiu recusa a entrega com a frase de quem opera', () => {
    // Uma entrega registrada antes da saída é um registro do que não aconteceu.
    const { store, toast } = setup({
      recordProof: () =>
        throwError(() => ({
          status: 409,
          code: 'delivery_not_recordable', detail: 'x', reasonCode: 'load_not_on_the_road',
        })),
    } as Partial<LoadsApi>);

    store.recordProof(carga(), 's1', 'DELIVERED', ['k1'], [], null, null);

    expect(toast.error).toHaveBeenCalledWith(
      'A carga ainda não saiu. Registre a saída antes das entregas.',
    );
  });

  it('sem nome de quem assinou, nenhuma assinatura é enviada', () => {
    // A mídia é dado pessoal: ela só existe com consentimento, e a entrega acontece do mesmo jeito.
    const recordProof = vi.fn().mockReturnValue(of({ id: 'p1' }));
    const { store } = setup({ recordProof } as Partial<LoadsApi>);

    store.recordProof(carga(), 's1', 'DELIVERED', ['k1'], [], null, null);

    expect(recordProof.mock.calls[0][2].signatureConsent).toBeNull();

    store.recordProof(carga(), 's1', 'DELIVERED', ['k1'], [], null, 'Bruno');

    expect(recordProof.mock.calls[1][2].signatureConsent).toMatchObject({
      consentedByName: 'Bruno',
      purpose: 'comprovar a entrega',
    });
  });

  it('os conflitos do aplicativo ficam esperando decisão, e não somem', () => {
    // Último-a-escrever-ganha descartaria em silêncio o registro de quem estava lá — ou o do
    // escritório —, e nos dois casos alguém descobre semanas depois sem saber o que perdeu.
    const { store } = setup({
      syncConflicts: () =>
        of([
          {
            clientOperationId: 'op1',
            sequence: 1,
            stopId: 's1',
            status: 'CONFLICTED',
            resultId: null,
            reason: 'Esta parada já foi registrada no servidor.',
            occurredAt: '2026-08-18T10:00:00Z',
            receivedAt: '2026-08-18T18:00:00Z',
            clockAhead: false,
          },
        ]),
    } as Partial<LoadsApi>);

    store.loadConflicts();

    expect(store.conflicts()).toHaveLength(1);
    expect(store.conflicts()[0].reason).toContain('já foi registrada');
    // O conflito não traz resultado: nada foi aplicado, e é isso que a tela precisa dizer.
    expect(store.conflicts()[0].resultId).toBeNull();
  });
});
