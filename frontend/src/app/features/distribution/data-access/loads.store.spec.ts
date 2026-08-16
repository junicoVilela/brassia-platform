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
          error: {
            code: 'separation_of_duties',
            detail: 'Quem montou a carga não pode liberá-la.',
          },
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
          error: { code: 'container_not_shippable', detail: 'recusado', reasonCode: 'not_released' },
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
});
