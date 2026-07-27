import { TestBed } from '@angular/core/testing';
import { of, throwError } from 'rxjs';
import { describe, expect, it, vi } from 'vitest';
import { IngredientsApi } from '../../catalog/data-access/ingredients.api';
import { PurchaseNeedsApi } from './purchase-needs.api';
import { PurchaseNeedsStore } from './purchase-needs.store';

function setup(api: Partial<PurchaseNeedsApi>, ingredients: Partial<IngredientsApi> = {}) {
  TestBed.configureTestingModule({
    providers: [
      PurchaseNeedsStore,
      { provide: PurchaseNeedsApi, useValue: api },
      { provide: IngredientsApi, useValue: { list: vi.fn(() => of({ content: [] })), ...ingredients } },
    ],
  });
  return TestBed.inject(PurchaseNeedsStore);
}

describe('PurchaseNeedsStore', () => {
  it('carrega necessidades (vazio)', () => {
    const list = vi.fn(() => of([]));
    const store = setup({ list });
    store.load();
    expect(list).toHaveBeenCalledOnce();
    expect(store.empty()).toBe(true);
  });

  it('expõe as sugestões carregadas', () => {
    const list = vi.fn(() =>
      of([{ ingredientId: 'i1', demand: 20, onHand: 5, reserved: 0, reorderPoint: 0, suggested: 15, unit: 'KG' }]));
    const store = setup({ list });
    store.load();
    expect(store.items().length).toBe(1);
    expect(store.items()[0].suggested).toBe(15);
    expect(store.empty()).toBe(false);
  });

  it('resolve o nome do ingrediente pelo catálogo', () => {
    const list = vi.fn(() => of([]));
    const ingredients = { list: vi.fn(() => of({ content: [{ id: 'i1', name: 'Pilsen' }] })) };
    const store = setup({ list }, ingredients as unknown as Partial<IngredientsApi>);
    store.load();
    expect(store.ingredientName('i1')).toBe('Pilsen');
    expect(store.ingredientName('x')).toBe('x');
  });

  it('reporta erro de carga', () => {
    const list = vi.fn(() => throwError(() => new Error('boom')));
    const store = setup({ list });
    store.load();
    expect(store.error()).not.toBeNull();
  });
});
