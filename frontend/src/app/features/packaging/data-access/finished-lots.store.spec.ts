import { TestBed } from '@angular/core/testing';
import { of, throwError } from 'rxjs';
import { describe, expect, it, vi } from 'vitest';
import { ToastService } from '../../../core/notifications/toast.service';
import { FinishedLot, Shipment } from '../domain/finished-lot.model';
import { FinishedLotsApi } from './finished-lots.api';
import { FinishedLotsStore } from './finished-lots.store';

function lot(over: Partial<FinishedLot> = {}): FinishedLot {
  return {
    id: 'l1',
    code: 'LOTE-100/1',
    runId: 'run1',
    planId: 'plan1',
    batchId: 'batch1',
    batchCode: 'LOTE-100',
    containerId: 'c1',
    containerVolumeMl: 355,
    units: 780,
    volumeLiters: 276.9,
    packagedOn: '2026-08-20',
    ...over,
  };
}

function shipment(over: Partial<Shipment> = {}): Shipment {
  return {
    id: 's1',
    finishedLotId: 'l1',
    destination: 'Bar do Zé',
    contact: null,
    units: 120,
    shippedOn: '2026-08-21',
    note: null,
    reversedAt: null,
    reversalReason: null,
    ...over,
  };
}

function setup(api: Partial<FinishedLotsApi> = {}): FinishedLotsStore {
  TestBed.configureTestingModule({
    providers: [
      FinishedLotsStore,
      {
        provide: FinishedLotsApi,
        useValue: {
          lots: () => of([lot()]),
          shipments: () => of([shipment()]),
          ship: () => of(shipment({ id: 's2', units: 60 })),
          ...api,
        },
      },
      { provide: ToastService, useValue: { success: vi.fn() } },
    ],
  });
  return TestBed.inject(FinishedLotsStore);
}

describe('FinishedLotsStore', () => {
  it('mostra quantas unidades ainda não têm destino registrado', () => {
    const store = setup();

    store.load();

    expect(store.rows()[0].shipped).toBe(120);
    // É este número que, num recall, ninguém sabe onde está.
    expect(store.rows()[0].remaining).toBe(660);
  });

  it('separa os lotes sem nenhuma saída', () => {
    const store = setup({
      lots: () => of([lot(), lot({ id: 'l2', code: 'LOTE-100/2' })]),
    });

    store.load();

    expect(store.withoutDestination().map(row => row.lot.id)).toEqual(['l2']);
  });

  it('registrar expedição reduz o saldo sem recarregar tudo', () => {
    const store = setup();
    store.load();

    store.ship({
      finishedLotId: 'l1',
      destination: 'Mercado Central',
      contact: null,
      units: 60,
      shippedOn: '2026-08-22',
      note: null,
    });

    expect(store.rows()[0].shipped).toBe(180);
    expect(store.rows()[0].remaining).toBe(600);
  });

  it('traduz a recusa de expedir mais do que o lote tem', () => {
    const store = setup({
      ship: () =>
        throwError(() => ({ status: 409, code: 'shipment_exceeds_lot', shipment: { available: 660 } })),
    });
    store.load();

    store.ship({
      finishedLotId: 'l1',
      destination: 'X',
      contact: null,
      units: 5000,
      shippedOn: '2026-08-22',
      note: null,
    });

    expect(store.actionError()).toContain('660');
  });

  it('mostra o bloqueio da quarentena com a mensagem que veio do servidor', () => {
    const store = setup({
      ship: () =>
        throwError(() => ({
          status: 409,
          code: 'packaging_blocked',
          blockers: [{ code: 'quarantined', message: 'Este item vem de LOTE-100, em quarentena: X.' }],
        })),
    });
    store.load();

    store.ship({
      finishedLotId: 'l1',
      destination: 'X',
      contact: null,
      units: 10,
      shippedOn: '2026-08-22',
      note: null,
    });

    expect(store.actionError()).toContain('em quarentena');
  });
});
