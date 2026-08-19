import { TestBed } from '@angular/core/testing';
import { of, throwError } from 'rxjs';
import { describe, expect, it, vi } from 'vitest';
import { ToastService } from '../../../core/notifications/toast.service';
import { Container, ContainerLoan } from '../domain/container.model';
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

function emprestimo(over: Partial<ContainerLoan> = {}): ContainerLoan {
  return {
    id: 'l1',
    containerId: 'c1',
    customerId: 'cli1',
    customerName: 'Bar do Bruno',
    lentAt: '2026-08-01T10:00:00Z',
    dueOn: '2026-08-31',
    overdue: false,
    daysLate: 0,
    depositAmount: 120,
    depositCurrency: 'BRL',
    depositOutcome: 'HELD',
    returnedAt: null,
    returnedLate: false,
    lostAt: null,
    lossReason: null,
    recoveredAt: null,
    recoveryReason: null,
    ...over,
  };
}

function setup(api: Partial<ContainersApi>) {
  const toast = { success: vi.fn(), error: vi.fn() };
  api.list ??= () => of([keg()]);
  api.identifiers ??= () => of([]);
  api.fills ??= () => of([]);
  api.locations ??= () => of([]);
  api.loans ??= () => of([]);
  api.sanitations ??= () => of([]);
  api.inspectionPolicies ??= () => of([]);
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
          code: 'container_not_fillable', detail: 'recusado', reasonCode: 'inspection_expired',
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
          code: 'identifier_in_use', detail: 'A etiqueta QR-1 já está em uso por outro contêiner.',
        })),
    } as Partial<ContainersApi>);

    store.openIdentifiers(keg());
    store.assign('QR-1', 'QR');

    expect(toast.error).toHaveBeenCalledWith(
      'A etiqueta QR-1 já está em uso por outro contêiner.',
    );
  });

  it('esvaziar anuncia fim de período, e nunca conteúdo apagado', () => {
    // O vínculo continua respondendo pelo passado: é ele que liga este keg àquele lote num recall.
    const { store, toast } = setup({ emptyFill: () => of(undefined) } as Partial<ContainersApi>);

    store.emptyFill(keg({ state: 'FILLED' }));

    expect(toast.success).toHaveBeenCalledWith(
      'Conteúdo encerrado. O registro do que esteve dentro continua.',
    );
  });

  it('o histórico traz o que esteve dentro e por onde andou', () => {
    // Não só o conteúdo de agora: um campo sobrescrito perderia "o que estava dentro em 12 de março".
    const { store } = setup({
      fills: () =>
        of([
          {
            id: 'f2',
            finishedLotId: 'l2',
            lotCode: 'L-2',
            volumeLiters: 50,
            filledAt: '2026-04-02T09:00:00Z',
            emptiedAt: null,
            current: true,
          },
          {
            id: 'f1',
            finishedLotId: 'l1',
            lotCode: 'L-1',
            volumeLiters: 50,
            filledAt: '2026-03-12T14:00:00Z',
            emptiedAt: '2026-03-22T14:00:00Z',
            current: false,
          },
        ]),
      locations: () =>
        of([{ id: 'p1', kind: 'CUSTOMER', place: 'Bar do Bruno', recordedAt: '2026-04-03T10:00:00Z' }]),
    } as Partial<ContainersApi>);

    store.openHistory(keg());

    expect(store.fills()).toHaveLength(2);
    expect(store.fills()[1].lotCode).toBe('L-1');
    expect(store.fills()[1].emptiedAt).not.toBeNull();
    expect(store.locations()[0].place).toBe('Bar do Bruno');
  });

  it('a recusa do conteúdo é outra frase que a recusa do vasilhame', () => {
    // Misturá-las daria ao operador uma mensagem que não diz o que trocar.
    const { store, toast } = setup({
      fill: () =>
        throwError(() => ({
          status: 409,
          code: 'fill_not_allowed', detail: 'recusado', reasonCode: 'over_capacity',
        })),
    } as Partial<ContainersApi>);

    store.fill(keg(), 'lote-1', 60);

    expect(toast.error).toHaveBeenCalledWith('O volume informado não cabe no vasilhame.');
  });

  it('conta atrasados, e não quem devolveu tarde', () => {
    // Contar os dois juntos faria a cobrança do dia ligar para quem já devolveu.
    const { store } = setup({
      loans: () =>
        of([
          emprestimo({ id: 'l1', overdue: true, daysLate: 5 }),
          emprestimo({ id: 'l2', overdue: false, daysLate: 0 }),
          emprestimo({
            id: 'l3',
            overdue: false,
            daysLate: 0,
            returnedAt: '2026-08-20T10:00:00Z',
            returnedLate: true,
            depositOutcome: 'TO_REFUND',
          }),
        ]),
    } as Partial<ContainersApi>);

    store.loadLoans();

    expect(store.overdueCount()).toBe(1);
  });

  it('a devolução fala em caução A DEVOLVER, e nunca em devolvida', () => {
    // O estorno é lançamento financeiro: dizer o contrário faria a tela afirmar um pagamento que
    // ninguém fez.
    const { store, toast } = setup({ returnLoan: () => of(undefined) } as Partial<ContainersApi>);

    store.returnLoan(emprestimo({}));

    expect(toast.success).toHaveBeenCalledWith(
      'Devolução registrada. A caução fica a devolver ao cliente.',
    );
  });

  it('a perda diz que o vasilhame saiu do inventário e a caução ficou', () => {
    // Perda não é descarte, e a frase é o que impede as duas coisas de virarem a mesma linha.
    const { store, toast } = setup({ declareLoss: () => of(undefined) } as Partial<ContainersApi>);

    store.declareLoss(emprestimo({}), 'o bar fechou');

    expect(toast.success).toHaveBeenCalledWith(
      'Perda registrada. O vasilhame saiu do inventário e a caução fica retida.',
    );
  });

  it('caução zero vira ausência, e não um valor retido de mentira', () => {
    // Zero somaria no relatório de valores retidos como se houvesse dinheiro parado.
    const lend = vi.fn().mockReturnValue(of({ id: 'l1' }));
    const { store } = setup({ lend } as Partial<ContainersApi>);

    store.lend(keg(), 'c1', 'Bar do Bruno', '2026-09-30', 0);

    expect(lend.mock.calls[0][1].depositAmount).toBeNull();
    expect(lend.mock.calls[0][1].depositCurrency).toBeNull();

    store.lend(keg(), 'c1', 'Bar do Bruno', '2026-09-30', 120);

    expect(lend.mock.calls[1][1].depositAmount).toBe(120);
    expect(lend.mock.calls[1][1].depositCurrency).toBe('BRL');
  });

  it('o vasilhame já emprestado é recusado com a frase de quem opera', () => {
    // O mesmo keg com dois clientes contabilizaria duas cauções.
    const { store, toast } = setup({
      lend: () =>
        throwError(() => ({
          status: 409,
          code: 'loan_not_allowed', detail: 'x', reasonCode: 'already_lent',
        })),
    } as Partial<ContainersApi>);

    store.lend(keg(), 'c1', 'Bar', '2026-09-30', null);

    expect(toast.error).toHaveBeenCalledWith(
      'Este vasilhame já está emprestado. O mesmo keg com dois clientes contabilizaria duas cauções.',
    );
  });

  it('a volta do perdido fala em inventário e caução, e nunca em perda desfeita', () => {
    // A perda aconteceu, e o registro dela continua. Dizer o contrário faria a tela prometer um
    // apagamento que não houve.
    const { store, toast } = setup({ recoverLoan: () => of(undefined) } as Partial<ContainersApi>);

    store.recoverLoan(emprestimo({ lostAt: '2026-09-01T10:00:00Z', lossReason: 'sumiu' }), 'reabriu');

    expect(toast.success).toHaveBeenCalledWith(
      'Vasilhame de volta ao inventário, para lavar. A caução volta a ser devida ao cliente.',
    );
  });

  it('a fila de perdidos ignora o que já voltou', () => {
    // Senão o mesmo keg apareceria para recuperar duas vezes.
    const { store } = setup({
      loans: () =>
        of([
          emprestimo({ id: 'l1', lostAt: '2026-09-01T10:00:00Z', lossReason: 'sumiu' }),
          emprestimo({
            id: 'l2',
            lostAt: '2026-09-01T10:00:00Z',
            lossReason: 'sumiu',
            recoveredAt: '2027-03-01T10:00:00Z',
            recoveryReason: 'voltou',
          }),
        ]),
    } as Partial<ContainersApi>);

    store.loadLoans();

    expect(store.lost()).toHaveLength(1);
    expect(store.lost()[0].id).toBe('l1');
  });

  it('sem política cadastrada não há sugestão, e nada afrouxa', () => {
    // A ausência não muda regra nenhuma: o vasilhame continua exigindo inspeção válida para encher.
    const { store } = setup({ inspectionSuggestion: () => of({}) } as Partial<ContainersApi>);

    store.loadSuggestion(keg());

    expect(store.suggestedValidUntil()).toBeNull();
  });

  it('a sugestão chega da política, e é só sugestão', () => {
    const { store } = setup({
      inspectionSuggestion: () => of({ performedAt: '2026-08-18T10:00:00Z', validUntil: '2031-08-18T10:00:00Z' }),
    } as Partial<ContainersApi>);

    store.loadSuggestion(keg());

    expect(store.suggestedValidUntil()).toBe('2031-08-18T10:00:00Z');
  });
});
