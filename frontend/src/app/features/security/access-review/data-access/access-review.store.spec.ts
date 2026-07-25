import { TestBed } from '@angular/core/testing';
import { of, throwError } from 'rxjs';
import { describe, expect, it, vi } from 'vitest';
import { ToastService } from '../../../../core/notifications/toast.service';
import { AccessReviewApi } from './access-review.api';
import { AccessReviewStore } from './access-review.store';

function setup(api: Partial<Record<keyof AccessReviewApi, unknown>>) {
  const toast = { success: vi.fn(), error: vi.fn() };
  const base = {
    listReviews: vi.fn(() => of([])),
    listRules: vi.fn(() => of([])),
    listUsers: vi.fn(() => of([])),
    listGroups: vi.fn(() => of([])),
    listPermissions: vi.fn(() => of([])),
  };
  TestBed.configureTestingModule({
    providers: [
      AccessReviewStore,
      { provide: AccessReviewApi, useValue: { ...base, ...api } },
      { provide: ToastService, useValue: toast },
    ],
  });
  return { store: TestBed.inject(AccessReviewStore), toast };
}

describe('AccessReviewStore', () => {
  it('init resolve nomes de usuário/grupo pelos catálogos', () => {
    const { store } = setup({
      listReviews: vi.fn(() => of([{ id: 'r1', name: 'Q3', status: 'OPEN', reviewerId: 'u1', dueAt: 'x' }])),
      listUsers: vi.fn(() => of([{ id: 'u1', name: 'Ana' }])),
      listGroups: vi.fn(() => of([{ id: 'g1', name: 'Admin' }])),
    });

    store.init();

    expect(store.reviews()).toHaveLength(1);
    expect(store.userName('u1')).toBe('Ana');
    expect(store.groupName('g1')).toBe('Admin');
    expect(store.userName('zzzzzzzz-0000')).toBe('zzzzzzzz');
  });

  it('seleciona revisão e carrega itens', () => {
    const { store } = setup({
      listItems: vi.fn(() => of([{ id: 'i1', userId: 'u1', groupId: 'g1', decision: 'PENDING' }])),
    });

    store.init();
    store.selectReview('r1');

    expect(store.items()).toHaveLength(1);
    expect(store.selectedReview()).toBe('r1');
  });

  it('decide REMOVE recarrega os itens da revisão selecionada', () => {
    const listItems = vi.fn(() => of([]));
    const { store, toast } = setup({ listItems, decideItem: vi.fn(() => of(undefined)) });

    store.init();
    store.selectReview('r1');
    store.decide('i1', 'REMOVE', 'motivo');

    expect(toast.success).toHaveBeenCalled();
    // uma vez no selectReview inicial + uma no reload pós-decisão
    expect(listItems).toHaveBeenCalledTimes(2);
  });

  it('erro ao criar regra vira actionError', () => {
    const { store } = setup({ createRule: vi.fn(() => throwError(() => ({ status: 409 }))) });

    store.init();
    store.createRule({ leftPermissionCode: 'a', rightPermissionCode: 'b', reason: 'r' });

    expect(store.actionError()).not.toBeNull();
    expect(store.submitting()).toBe(false);
  });
});
