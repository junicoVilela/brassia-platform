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
});
