import { TestBed } from '@angular/core/testing';
import { of, throwError } from 'rxjs';
import { describe, expect, it, vi } from 'vitest';
import { ToastService } from '../../../core/notifications/toast.service';
import { ActivityApi } from './activity.api';
import { ActivityStore } from './activity.store';

function setup(api: Partial<Record<keyof ActivityApi, unknown>>) {
  const toast = { success: vi.fn(), error: vi.fn() };
  TestBed.configureTestingModule({
    providers: [
      ActivityStore,
      { provide: ActivityApi, useValue: api },
      { provide: ToastService, useValue: toast },
    ],
  });
  return { store: TestBed.inject(ActivityStore), toast };
}

const session = (ref: string, current: boolean) => ({
  ref, createdAt: '2026-01-01T00:00:00Z', lastAccessedAt: '2026-01-01T01:00:00Z', current,
});

describe('ActivityStore', () => {
  it('carrega sessões e detecta outras além da atual', () => {
    const { store } = setup({ listSessions: vi.fn(() => of([session('a', true), session('b', false)])) });

    store.loadSessions();

    expect(store.sessions()).toHaveLength(2);
    expect(store.hasOtherSessions()).toBe(true);
    expect(store.error()).toBeNull();
  });

  it('não aponta outras sessões quando só existe a atual', () => {
    const { store } = setup({ listSessions: vi.fn(() => of([session('a', true)])) });

    store.loadSessions();

    expect(store.hasOtherSessions()).toBe(false);
  });

  it('revoga uma sessão e recarrega a lista', () => {
    const listSessions = vi.fn(() => of([session('a', true)]));
    const { store, toast } = setup({ revokeSession: vi.fn(() => of(undefined)), listSessions });

    store.revoke('b');

    expect(toast.success).toHaveBeenCalledOnce();
    expect(listSessions).toHaveBeenCalledOnce();
  });

  it('marca erro quando o histórico falha', () => {
    const { store } = setup({ loginHistory: vi.fn(() => throwError(() => new Error('boom'))) });

    store.loadHistory();

    expect(store.error()).not.toBeNull();
    expect(store.loadingHistory()).toBe(false);
  });
});
