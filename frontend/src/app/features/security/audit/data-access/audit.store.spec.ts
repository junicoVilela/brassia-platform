import { TestBed } from '@angular/core/testing';
import { of, throwError } from 'rxjs';
import { describe, expect, it, vi } from 'vitest';
import { AuditApi } from './audit.api';
import { AuditStore } from './audit.store';

function page(content: unknown[], p = 0, totalPages = 1, totalElements = content.length) {
  return of({ content, page: p, size: 25, totalElements, totalPages });
}

function setup(api: Partial<Record<keyof AuditApi, unknown>>) {
  const base = { search: vi.fn(() => page([])), listUsers: vi.fn(() => of([])) };
  TestBed.configureTestingModule({ providers: [AuditStore, { provide: AuditApi, useValue: { ...base, ...api } }] });
  return { store: TestBed.inject(AuditStore), api: { ...base, ...api } };
}

describe('AuditStore', () => {
  it('init carrega a primeira página e resolve nome do ator', () => {
    const { store } = setup({
      search: vi.fn(() => page([{ actorId: 'u1' }], 0, 3, 60)),
      listUsers: vi.fn(() => of([{ id: 'u1', displayName: 'Ana' }])),
    });

    store.init();

    expect(store.events()).toHaveLength(1);
    expect(store.totalPages()).toBe(3);
    expect(store.actorName('u1')).toBe('Ana');
    expect(store.actorName('zzzzzzzz-1')).toBe('zzzzzzzz');
  });

  it('applyFilter repassa filtros ao backend e volta à página 0', () => {
    const search = vi.fn(() => page([]));
    const { store } = setup({ search });

    store.applyFilter({ action: 'login' });

    expect(search).toHaveBeenCalledWith(
      expect.objectContaining({ action: 'login' }), 0, 25,
    );
    expect(store.filter().action).toBe('login');
  });

  it('nextPage só avança quando há próxima', () => {
    const search = vi.fn(() => page([{}], 0, 2, 40));
    const { store } = setup({ search });

    store.load(0);
    store.nextPage();

    expect(search).toHaveBeenLastCalledWith(expect.anything(), 1, 25);
  });

  it('marca erro quando a carga falha', () => {
    const { store } = setup({ search: vi.fn(() => throwError(() => new Error('boom'))) });
    store.load(0);
    expect(store.error()).not.toBeNull();
    expect(store.loading()).toBe(false);
  });
});
