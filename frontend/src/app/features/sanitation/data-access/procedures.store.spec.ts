import { TestBed } from '@angular/core/testing';
import { of, throwError } from 'rxjs';
import { describe, expect, it, vi } from 'vitest';
import { ToastService } from '../../../core/notifications/toast.service';
import { ProceduresApi } from './procedures.api';
import { ProceduresStore } from './procedures.store';

function setup(api: Partial<ProceduresApi>) {
  TestBed.configureTestingModule({
    providers: [
      ProceduresStore,
      { provide: ProceduresApi, useValue: api },
      { provide: ToastService, useValue: { success: vi.fn(), error: vi.fn() } },
    ],
  });
  return TestBed.inject(ProceduresStore);
}

describe('ProceduresStore', () => {
  it('carrega POPs (vazio)', () => {
    const list = vi.fn(() => of([]));
    const store = setup({ list });
    store.load();
    expect(list).toHaveBeenCalledOnce();
    expect(store.empty()).toBe(true);
  });

  it('cria e recarrega', () => {
    const create = vi.fn(() => of({ id: 'p1', version: 1 }));
    const list = vi.fn(() => of([]));
    const onSuccess = vi.fn();
    const store = setup({ create, list });
    store.create({ code: 'CIP', name: 'x', steps: [] }, onSuccess);
    expect(create).toHaveBeenCalledOnce();
    expect(onSuccess).toHaveBeenCalledOnce();
  });

  it('mostra conflito de rascunho duplicado', () => {
    const create = vi.fn(() => throwError(() => ({ status: 409 })));
    const list = vi.fn(() => of([]));
    const store = setup({ create, list });
    store.create({ code: 'CIP', name: 'x', steps: [] });
    expect(store.actionError()).not.toBeNull();
  });
});
