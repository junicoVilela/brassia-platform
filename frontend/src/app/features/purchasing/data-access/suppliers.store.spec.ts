import { TestBed } from '@angular/core/testing';
import { of } from 'rxjs';
import { describe, expect, it, vi } from 'vitest';
import { ToastService } from '../../../core/notifications/toast.service';
import { SuppliersApi } from './suppliers.api';
import { SuppliersStore } from './suppliers.store';

function setup(api: Partial<SuppliersApi>) {
  TestBed.configureTestingModule({
    providers: [
      SuppliersStore,
      { provide: SuppliersApi, useValue: api },
      { provide: ToastService, useValue: { success: vi.fn() } },
    ],
  });
  return TestBed.inject(SuppliersStore);
}

describe('SuppliersStore', () => {
  it('carrega fornecedores (vazio)', () => {
    const list = vi.fn(() => of([]));
    const store = setup({ list });
    store.load();
    expect(list).toHaveBeenCalledOnce();
    expect(store.empty()).toBe(true);
  });

  it('cadastra e recarrega', () => {
    const create = vi.fn(() => of({ id: 's1', name: 'Maltaria', code: 'MS' }));
    const list = vi.fn(() => of([]));
    const onSuccess = vi.fn();
    const store = setup({ create, list });
    store.create({ name: 'Maltaria', code: 'MS' }, onSuccess);
    expect(create).toHaveBeenCalledOnce();
    expect(onSuccess).toHaveBeenCalledOnce();
    expect(list).toHaveBeenCalled();
  });
});
