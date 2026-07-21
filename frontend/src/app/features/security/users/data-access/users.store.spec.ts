import { TestBed } from '@angular/core/testing';
import { of, throwError } from 'rxjs';
import { describe, expect, it, vi } from 'vitest';
import { UsersApi } from './users.api';
import { UsersStore } from './users.store';

function page(content: unknown[]) {
  return of({ content, page: 0, size: 20, totalElements: content.length, totalPages: 1 });
}

describe('UsersStore', () => {
  it('carrega usuários e reflete estado vazio', () => {
    const api = { list: vi.fn(() => page([])) };
    TestBed.configureTestingModule({ providers: [UsersStore, { provide: UsersApi, useValue: api }] });
    const store = TestBed.inject(UsersStore);

    store.load();

    expect(api.list).toHaveBeenCalledOnce();
    expect(store.empty()).toBe(true);
    expect(store.error()).toBeNull();
  });

  it('marca erro quando a listagem falha', () => {
    const api = { list: vi.fn(() => throwError(() => new Error('boom'))) };
    TestBed.configureTestingModule({ providers: [UsersStore, { provide: UsersApi, useValue: api }] });
    const store = TestBed.inject(UsersStore);

    store.load();

    expect(store.error()).not.toBeNull();
    expect(store.loading()).toBe(false);
  });

  it('convida e recarrega a lista no sucesso', () => {
    const api = {
      list: vi.fn(() => page([{ id: '1', email: 'a@x.com', displayName: 'Ana', status: 'INVITED', emailVerifiedAt: null }])),
      invite: vi.fn(() => of({ userId: '1', email: 'a@x.com', status: 'INVITED' })),
    };
    TestBed.configureTestingModule({ providers: [UsersStore, { provide: UsersApi, useValue: api }] });
    const store = TestBed.inject(UsersStore);

    const onSuccess = vi.fn();
    store.invite({ email: 'a@x.com', displayName: 'Ana' }, onSuccess);

    expect(api.invite).toHaveBeenCalledOnce();
    expect(onSuccess).toHaveBeenCalledOnce();
    expect(api.list).toHaveBeenCalledOnce();
    expect(store.items()).toHaveLength(1);
  });

  it('marca actionError quando o convite falha', () => {
    const api = {
      list: vi.fn(() => page([])),
      invite: vi.fn(() => throwError(() => ({ status: 409 }))),
    };
    TestBed.configureTestingModule({ providers: [UsersStore, { provide: UsersApi, useValue: api }] });
    const store = TestBed.inject(UsersStore);

    store.invite({ email: 'a@x.com', displayName: 'Ana' });

    expect(store.actionError()).not.toBeNull();
    expect(store.submitting()).toBe(false);
  });
});
