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

  const user = { id: '42', email: 'a@x.com', displayName: 'Ana', status: 'ACTIVE' as const, emailVerifiedAt: null };

  it('seleciona usuário, carrega grupos e calcula disponíveis', () => {
    const api = {
      listMemberships: vi.fn(() => of([{ groupId: 'g1', code: 'A', name: 'Admin' }])),
      listGroups: vi.fn(() => of([
        { groupId: 'g1', code: 'A', name: 'Admin' },
        { groupId: 'g2', code: 'B', name: 'Brewers' },
      ])),
    };
    TestBed.configureTestingModule({ providers: [UsersStore, { provide: UsersApi, useValue: api }] });
    const store = TestBed.inject(UsersStore);

    store.selectUser(user);

    expect(store.memberships()).toHaveLength(1);
    expect(store.availableGroups().map(g => g.groupId)).toEqual(['g2']);
  });

  it('bloqueio por segregação (409) vira membershipError', () => {
    const api = {
      listMemberships: vi.fn(() => of([])),
      listGroups: vi.fn(() => of([])),
      grantMembership: vi.fn(() => throwError(() => ({ status: 409 }))),
    };
    TestBed.configureTestingModule({ providers: [UsersStore, { provide: UsersApi, useValue: api }] });
    const store = TestBed.inject(UsersStore);

    store.selectUser(user);
    store.grantMembership('g2');

    expect(store.membershipError()).toContain('segregação');
  });

  it('associa com sucesso e recarrega os grupos', () => {
    const listMemberships = vi.fn(() => of([{ groupId: 'g1', code: 'A', name: 'Admin' }]));
    const api = {
      listMemberships,
      listGroups: vi.fn(() => of([])),
      grantMembership: vi.fn(() => of(undefined)),
    };
    TestBed.configureTestingModule({ providers: [UsersStore, { provide: UsersApi, useValue: api }] });
    const store = TestBed.inject(UsersStore);

    store.selectUser(user);
    store.grantMembership('g1');

    expect(api.grantMembership).toHaveBeenCalledWith('42', 'g1');
    expect(listMemberships).toHaveBeenCalledTimes(2);
    expect(store.membershipError()).toBeNull();
  });
});
