import { TestBed } from '@angular/core/testing';
import { of } from 'rxjs';
import { describe, expect, it, vi } from 'vitest';
import { IngredientsApi } from '../../catalog/data-access/ingredients.api';
import { SuppliersApi } from '../../purchasing/data-access/suppliers.api';
import { ToastService } from '../../../core/notifications/toast.service';
import { StockLot } from '../domain/stock-lot.model';
import { InventoryApi } from './inventory.api';
import { InventoryStore } from './inventory.store';

function emptyPage() {
  return of({ content: [], page: 0, size: 20, totalElements: 0, totalPages: 0 });
}

function setup(api: Partial<InventoryApi>) {
  TestBed.configureTestingModule({
    providers: [
      InventoryStore,
      { provide: InventoryApi, useValue: api },
      { provide: IngredientsApi, useValue: { list: vi.fn(emptyPage) } },
      { provide: SuppliersApi, useValue: { list: vi.fn(() => of([])) } },
      { provide: ToastService, useValue: { success: vi.fn() } },
    ],
  });
  return TestBed.inject(InventoryStore);
}

describe('InventoryStore', () => {
  it('carrega lotes (vazio)', () => {
    const list = vi.fn(() => of([]));
    const store = setup({ list });
    store.load();
    expect(list).toHaveBeenCalledOnce();
    expect(store.empty()).toBe(true);
  });

  it('recebe um lote e recarrega', () => {
    const receive = vi.fn(() => of({ id: 'l1' } as unknown as StockLot));
    const list = vi.fn(() => of([]));
    const onSuccess = vi.fn();
    const store = setup({ receive, list });
    store.receive({
      ingredientId: 'i', supplierId: 's', quantity: 25, unit: 'KG', unitCost: 4.5, inspection: 'APPROVED',
    }, onSuccess);
    expect(receive).toHaveBeenCalledOnce();
    expect(onSuccess).toHaveBeenCalledOnce();
    expect(list).toHaveBeenCalled();
  });

  it('abre movimentos e carrega saldo + ledger', () => {
    const balance = vi.fn(() => of({ onHand: 25, reserved: 0, available: 25 }));
    const movements = vi.fn(() => of([{ id: 'm1', type: 'ENTRY', quantity: 25 } as never]));
    const store = setup({ balance, movements });
    store.showMovements('l1');
    expect(store.movementsLotId()).toBe('l1');
    expect(balance).toHaveBeenCalledWith('l1');
    expect(movements).toHaveBeenCalledWith('l1');
    expect(store.balance()?.onHand).toBe(25);
    expect(store.movements()).toHaveLength(1);
    store.showMovements('l1'); // alterna (fecha)
    expect(store.movementsLotId()).toBeNull();
  });

  it('reserva estoque (FEFO) e guarda o resultado', () => {
    const reserve = vi.fn(() => of({ ingredientId: 'i', reservedQuantity: 15, unit: 'KG',
      allocations: [{ lotId: 'l1', quantity: 10, unit: 'KG' }, { lotId: 'l2', quantity: 5, unit: 'KG' }] }));
    const list = vi.fn(() => of([]));
    const onSuccess = vi.fn();
    const store = setup({ reserve, list });
    store.reserve({ ingredientId: 'i', quantity: 15, unit: 'KG' }, onSuccess);
    expect(reserve).toHaveBeenCalledOnce();
    expect(onSuccess).toHaveBeenCalledOnce();
    expect(store.reservation()?.allocations).toHaveLength(2);
  });

  it('registra movimento e atualiza saldo', () => {
    const recordMovement = vi.fn(() => of({ onHand: 15, reserved: 0, available: 15 }));
    const balance = vi.fn(() => of({ onHand: 15, reserved: 0, available: 15 }));
    const movements = vi.fn(() => of([]));
    const onSuccess = vi.fn();
    const store = setup({ recordMovement, balance, movements });
    store.recordMovement('l1', { type: 'CONSUMPTION', quantity: 10 }, onSuccess);
    expect(recordMovement).toHaveBeenCalledWith('l1', { type: 'CONSUMPTION', quantity: 10 });
    expect(onSuccess).toHaveBeenCalledOnce();
    expect(balance).toHaveBeenCalledWith('l1');
  });
});
