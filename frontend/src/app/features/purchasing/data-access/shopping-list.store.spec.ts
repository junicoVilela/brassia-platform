import { TestBed } from '@angular/core/testing';
import { of, throwError } from 'rxjs';
import { describe, expect, it, vi } from 'vitest';
import { ShoppingListApi } from './shopping-list.api';
import { ShoppingListStore } from './shopping-list.store';

function setup(api: Partial<ShoppingListApi>) {
  TestBed.configureTestingModule({
    providers: [ShoppingListStore, { provide: ShoppingListApi, useValue: api }],
  });
  return TestBed.inject(ShoppingListStore);
}

describe('ShoppingListStore', () => {
  it('carrega grupos (vazio)', () => {
    const list = vi.fn(() => of([]));
    const store = setup({ list });
    store.load();
    expect(list).toHaveBeenCalledOnce();
    expect(store.empty()).toBe(true);
  });

  it('expõe os grupos por fornecedor', () => {
    const list = vi.fn(() => of([
      { supplierId: 's1', supplierName: 'Maltaria', items: [], estimatedTotal: 67.5 },
    ]));
    const store = setup({ list });
    store.load();
    expect(store.groups().length).toBe(1);
    expect(store.groups()[0].supplierName).toBe('Maltaria');
    expect(store.empty()).toBe(false);
  });

  it('reporta erro de carga', () => {
    const list = vi.fn(() => throwError(() => new Error('boom')));
    const store = setup({ list });
    store.load();
    expect(store.error()).not.toBeNull();
  });
});
