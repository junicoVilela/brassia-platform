import { TestBed } from '@angular/core/testing';
import { of, throwError } from 'rxjs';
import { describe, expect, it, vi } from 'vitest';
import { ToastService } from '../../../../core/notifications/toast.service';
import { TemporaryAccessApi } from './temporary-access.api';
import { TemporaryAccessStore } from './temporary-access.store';

function setup(api: Partial<Record<keyof TemporaryAccessApi, unknown>>) {
  const toast = { success: vi.fn(), error: vi.fn() };
  TestBed.configureTestingModule({
    providers: [
      TemporaryAccessStore,
      { provide: TemporaryAccessApi, useValue: api },
      { provide: ToastService, useValue: toast },
    ],
  });
  return { store: TestBed.inject(TemporaryAccessStore), toast };
}

describe('TemporaryAccessStore', () => {
  it('init carrega concessões e catálogos, resolvendo nomes', () => {
    const { store } = setup({
      list: vi.fn(() => of([{ id: '1', userId: 'u1', status: 'ACTIVE' }])),
      listUsers: vi.fn(() => of([{ id: 'u1', displayName: 'Ana' }])),
      listPermissions: vi.fn(() => of([])),
    });

    store.init();

    expect(store.grants()).toHaveLength(1);
    expect(store.nameFor('u1')).toBe('Ana');
    expect(store.nameFor(null)).toBe('—');
  });

  it('cai para o UUID abreviado quando o catálogo de usuários falha', () => {
    const { store } = setup({
      list: vi.fn(() => of([])),
      listUsers: vi.fn(() => throwError(() => ({ status: 403 }))),
      listPermissions: vi.fn(() => throwError(() => ({ status: 403 }))),
    });

    store.init();

    expect(store.nameFor('abcdef12-3456')).toBe('abcdef12');
    expect(store.error()).toBeNull();
  });

  it('aprovação bloqueada (409) explica autoaprovação', () => {
    const { store } = setup({
      list: vi.fn(() => of([])),
      approve: vi.fn(() => throwError(() => ({ status: 409 }))),
    });

    store.approve('g1');

    expect(store.actionError()).toContain('aprovador');
  });

  it('solicita e recarrega no sucesso', () => {
    const list = vi.fn(() => of([]));
    const { store, toast } = setup({ list, request: vi.fn(() => of({ id: 'g1' })) });
    const onSuccess = vi.fn();

    store.request({ userId: 'u1', permissionCode: 'recipe.read', reason: 'x', durationHours: 8 }, onSuccess);

    expect(onSuccess).toHaveBeenCalledOnce();
    expect(toast.success).toHaveBeenCalledOnce();
    expect(list).toHaveBeenCalledOnce();
  });
});
