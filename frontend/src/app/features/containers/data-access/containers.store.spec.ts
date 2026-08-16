import { TestBed } from '@angular/core/testing';
import { of, throwError } from 'rxjs';
import { describe, expect, it, vi } from 'vitest';
import { ToastService } from '../../../core/notifications/toast.service';
import { Container } from '../domain/container.model';
import { ContainersApi } from './containers.api';
import { ContainersStore } from './containers.store';

function keg(over: Partial<Container> = {}): Container {
  return {
    id: 'c1',
    code: 'KEG-0001',
    kind: 'KEG',
    nominalCapacityLiters: 50,
    ownership: 'OWN',
    condition: 'GOOD',
    state: 'EMPTY',
    inspectionValidUntil: '2027-08-16T00:00:00Z',
    fillable: true,
    retiredAt: null,
    retirementReason: null,
    ...over,
  };
}

function setup(api: Partial<ContainersApi>) {
  const toast = { success: vi.fn(), error: vi.fn() };
  api.list ??= () => of([keg()]);
  api.identifiers ??= () => of([]);
  TestBed.configureTestingModule({
    providers: [
      ContainersStore,
      { provide: ContainersApi, useValue: api },
      { provide: ToastService, useValue: toast },
    ],
  });
  return { store: TestBed.inject(ContainersStore), toast };
}

describe('ContainersStore', () => {
  it('separa o que está pronto do que voltou sujo', () => {
    // "Voltou" não é "pronto". Contar os dois juntos faria o encarregado planejar o dia com kegs que
    // ninguém lavou.
    const { store } = setup({
      list: () =>
        of([
          keg(),
          keg({ id: 'c2', state: 'RETURNED', fillable: false }),
          keg({ id: 'c3', state: 'RETURNED', fillable: false }),
        ]),
    } as Partial<ContainersApi>);

    store.load();

    expect(store.readyToFill()).toBe(1);
    expect(store.awaitingCleaning()).toBe(2);
  });

  it('o contêiner recém-cadastrado é anunciado como ainda não enchível', () => {
    // Dizer só "cadastrado" deixaria o operador achar que o keg já serve — e ele não serve até alguém
    // atestar a inspeção.
    const { store, toast } = setup({ register: () => of({ id: 'c9' }) } as Partial<ContainersApi>);

    store.register('KEG-9', 'KEG', 50, 'OWN');

    expect(toast.success).toHaveBeenCalledWith(
      'Contêiner cadastrado. Ele só pode ser enchido depois da inspeção.',
    );
  });

  it('a recusa de encher vira a frase do motivo, e não um erro genérico', () => {
    // Sem isso o operador tentaria outro keg até um passar, sem nunca saber o que havia de errado com o
    // primeiro.
    const { store, toast } = setup({
      move: () =>
        throwError(() => ({
          status: 409,
          error: { code: 'container_not_fillable', detail: 'recusado', reasonCode: 'inspection_expired' },
        })),
    } as Partial<ContainersApi>);

    store.move(keg(), 'FILLED');

    expect(toast.error).toHaveBeenCalledWith(
      'A inspeção está vencida. Vaso de pressão sem inspeção em dia é risco físico.',
    );
  });

  it('mover relê a lista, porque "pronto" é composto no servidor', () => {
    // Uma lista em cache mostraria como disponível um keg que acabou de voltar sujo.
    const list = vi
      .fn()
      .mockReturnValueOnce(of([keg()]))
      .mockReturnValueOnce(of([keg({ state: 'FILLED', fillable: false })]));
    const { store } = setup({ list, move: () => of(undefined) } as Partial<ContainersApi>);

    store.load();
    store.move(keg(), 'FILLED');

    expect(store.containers()[0].state).toBe('FILLED');
    expect(store.readyToFill()).toBe(0);
  });

  it('ler um código devolve um vasilhame, e nada além disso', () => {
    // Ler identifica, e não autoriza: o resultado é informação na tela, e não uma sessão.
    const { store } = setup({ resolve: () => of(keg({ code: 'KEG-0042' })) } as Partial<ContainersApi>);

    store.scan('QR-1');

    expect(store.scanned()?.code).toBe('KEG-0042');
    store.clearScan();
    expect(store.scanned()).toBeNull();
  });

  it('a etiqueta já em uso é recusada com a mensagem do servidor', () => {
    // A garantia é o índice único parcial; a tela só precisa contar o que aconteceu.
    const { store, toast } = setup({
      assign: () =>
        throwError(() => ({
          status: 409,
          error: { code: 'identifier_in_use', detail: 'A etiqueta QR-1 já está em uso por outro contêiner.' },
        })),
    } as Partial<ContainersApi>);

    store.openIdentifiers(keg());
    store.assign('QR-1', 'QR');

    expect(toast.error).toHaveBeenCalledWith(
      'A etiqueta QR-1 já está em uso por outro contêiner.',
    );
  });
});
