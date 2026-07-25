import { TestBed } from '@angular/core/testing';
import { of, throwError } from 'rxjs';
import { describe, expect, it, vi } from 'vitest';
import { AuditApi } from './audit.api';
import { AuditStore } from './audit.store';

const event = (over: Partial<Record<string, unknown>> = {}) => ({
  occurredAt: '2026-07-01T12:00:00Z', action: 'security.login.success', outcome: 'SUCCESS',
  targetType: 'security_user', targetId: 't1', actorId: 'u1', changeSummary: '', ...over,
});

function setup(events: unknown[], users: unknown[] = []) {
  const api = { list: vi.fn(() => of(events)), listUsers: vi.fn(() => of(users)) };
  TestBed.configureTestingModule({ providers: [AuditStore, { provide: AuditApi, useValue: api }] });
  return { store: TestBed.inject(AuditStore), api };
}

describe('AuditStore', () => {
  it('carrega eventos e resolve nome do ator', () => {
    const { store } = setup([event()], [{ id: 'u1', displayName: 'Ana' }]);
    store.load();
    expect(store.filtered()).toHaveLength(1);
    expect(store.actorName('u1')).toBe('Ana');
    expect(store.actorName('zzzzzzzz-1')).toBe('zzzzzzzz');
  });

  it('filtra por termo (ação/recurso/ator)', () => {
    const { store } = setup(
      [
        event({ action: 'catalog.ingredient.create', actorId: 'u1' }),
        event({ action: 'security.login.success', actorId: 'u2' }),
      ],
      [{ id: 'u1', displayName: 'Ana' }],
    );
    store.load();

    store.term.set('ingredient');
    expect(store.filtered()).toHaveLength(1);

    store.term.set('ana');
    expect(store.filtered()).toHaveLength(1);
  });

  it('filtra por resultado e período', () => {
    const { store } = setup([
      event({ occurredAt: '2026-07-01T00:00:00Z', outcome: 'SUCCESS' }),
      event({ occurredAt: '2026-07-10T00:00:00Z', outcome: 'FAILURE' }),
    ]);
    store.load();

    store.outcome.set('FAILURE');
    expect(store.filtered()).toHaveLength(1);

    store.outcome.set('');
    store.from.set('2026-07-05T00:00');
    expect(store.filtered()).toHaveLength(1);
  });

  it('marca erro quando a carga falha', () => {
    const api = { list: vi.fn(() => throwError(() => new Error('boom'))), listUsers: vi.fn(() => of([])) };
    TestBed.configureTestingModule({ providers: [AuditStore, { provide: AuditApi, useValue: api }] });
    const store = TestBed.inject(AuditStore);
    store.load();
    expect(store.error()).not.toBeNull();
  });
});
