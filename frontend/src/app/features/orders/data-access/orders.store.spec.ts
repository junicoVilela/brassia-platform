import { TestBed } from '@angular/core/testing';
import { of } from 'rxjs';
import { describe, expect, it, vi } from 'vitest';
import { RecipesApi } from '../../recipes/data-access/recipes.api';
import { ToastService } from '../../../core/notifications/toast.service';
import { BrewOrderDetail } from '../domain/order.model';
import { OrdersApi } from './orders.api';
import { OrdersStore } from './orders.store';

function emptyPage() {
  return of({ content: [], page: 0, size: 20, totalElements: 0, totalPages: 0 });
}

function setup(orders: Partial<OrdersApi>) {
  TestBed.configureTestingModule({
    providers: [
      OrdersStore,
      { provide: OrdersApi, useValue: orders },
      { provide: RecipesApi, useValue: { list: vi.fn(emptyPage) } },
      { provide: ToastService, useValue: { success: vi.fn() } },
    ],
  });
  return TestBed.inject(OrdersStore);
}

describe('OrdersStore', () => {
  it('carrega as ordens (vazio)', () => {
    const list = vi.fn(emptyPage);
    const store = setup({ list });
    store.load();
    expect(list).toHaveBeenCalledOnce();
    expect(store.empty()).toBe(true);
  });

  it('cria uma OP e recarrega', () => {
    const create = vi.fn(() => of({ id: 'x', code: 'OP-2026-0001', status: 'DRAFT' }));
    const list = vi.fn(emptyPage);
    const onSuccess = vi.fn();
    const store = setup({ create, list });
    store.create({ recipeId: 'r', volumeLiters: 400 }, onSuccess);
    expect(create).toHaveBeenCalledOnce();
    expect(onSuccess).toHaveBeenCalledOnce();
    expect(list).toHaveBeenCalled();
  });

  it('carrega e alterna o detalhe (snapshot)', () => {
    const detail = { id: 'o1', code: 'OP-2026-0001' } as unknown as BrewOrderDetail;
    const get = vi.fn(() => of(detail));
    const store = setup({ get });
    store.showDetail('o1');
    expect(get).toHaveBeenCalledWith('o1');
    expect(store.detail()?.id).toBe('o1');
    store.showDetail('o1');
    expect(store.detail()).toBeNull();
  });
});
