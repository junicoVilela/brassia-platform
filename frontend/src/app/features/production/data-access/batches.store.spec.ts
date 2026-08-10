import { TestBed } from '@angular/core/testing';
import { of, throwError } from 'rxjs';
import { describe, expect, it, vi } from 'vitest';
import { AuthService } from '../../../core/auth/auth.service';
import { ToastService } from '../../../core/notifications/toast.service';
import { BatchesApi } from './batches.api';
import { BatchesStore } from './batches.store';

function setup(api: Partial<BatchesApi>) {
  TestBed.configureTestingModule({
    providers: [
      BatchesStore,
      { provide: BatchesApi, useValue: api },
      { provide: ToastService, useValue: { success: vi.fn(), error: vi.fn() } },
      { provide: AuthService, useValue: { hasPermission: () => true } },
    ],
  });
  return TestBed.inject(BatchesStore);
}

/** Envelope de paginação (REL-002): a listagem deixou de devolver array cru. */
function pagina<T>(content: T[]) {
  return { content, page: 0, size: 20, totalElements: content.length, totalPages: 1 };
}

describe('BatchesStore', () => {
  it('carrega lotes (vazio)', () => {
    const list = vi.fn(() => of(pagina([])));
    const store = setup({ list });
    store.load();
    expect(list).toHaveBeenCalledOnce();
    expect(store.empty()).toBe(true);
  });

  it('expõe os lotes carregados e alterna o expandido', () => {
    const list = vi.fn(() => of(pagina([
      { id: 'b1', orderId: 'o1', code: 'OP-1', recipeId: 'r1', recipeVersion: 1, recipeName: 'IPA',
        volumeLiters: 400, status: 'IN_PROGRESS', startedAt: '2026-07-27T00:00:00Z', steps: [] },
    ])));
    const store = setup({ list });
    store.load();
    expect(store.items().length).toBe(1);
    store.toggle('b1');
    expect(store.expandedId()).toBe('b1');
    store.toggle('b1');
    expect(store.expandedId()).toBeNull();
  });

  it('reporta erro de carga', () => {
    const list = vi.fn(() => throwError(() => new Error('boom')));
    const store = setup({ list });
    store.load();
    expect(store.error()).not.toBeNull();
  });
});
