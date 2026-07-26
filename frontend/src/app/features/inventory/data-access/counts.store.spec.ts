import { TestBed } from '@angular/core/testing';
import { of } from 'rxjs';
import { describe, expect, it, vi } from 'vitest';
import { ToastService } from '../../../core/notifications/toast.service';
import { CountsApi } from './counts.api';
import { CountsStore } from './counts.store';
import { InventoryApi } from './inventory.api';

function setup(api: Partial<CountsApi>) {
  TestBed.configureTestingModule({
    providers: [
      CountsStore,
      { provide: CountsApi, useValue: api },
      { provide: InventoryApi, useValue: { list: vi.fn(() => of([])) } },
      { provide: ToastService, useValue: { success: vi.fn() } },
    ],
  });
  return TestBed.inject(CountsStore);
}

describe('CountsStore', () => {
  it('carrega contagens (vazio)', () => {
    const list = vi.fn(() => of([]));
    const store = setup({ list });
    store.load();
    expect(list).toHaveBeenCalledOnce();
    expect(store.empty()).toBe(true);
  });

  it('cria contagem e recarrega', () => {
    const create = vi.fn(() => of({ id: 'c1', status: 'OPEN' }));
    const list = vi.fn(() => of([]));
    const onSuccess = vi.fn();
    const store = setup({ create, list });
    store.create({ lines: [{ lotId: 'l1', countedQuantity: 20 }] }, onSuccess);
    expect(create).toHaveBeenCalledOnce();
    expect(onSuccess).toHaveBeenCalledOnce();
    expect(list).toHaveBeenCalled();
  });

  it('aprova contagem e recarrega', () => {
    const approve = vi.fn(() => of({ id: 'c1', status: 'APPROVED', adjustments: 1 }));
    const list = vi.fn(() => of([]));
    const store = setup({ approve, list });
    store.approve('c1');
    expect(approve).toHaveBeenCalledWith('c1');
    expect(list).toHaveBeenCalled();
  });
});
