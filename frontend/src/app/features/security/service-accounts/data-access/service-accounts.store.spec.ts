import { TestBed } from '@angular/core/testing';
import { of, throwError } from 'rxjs';
import { describe, expect, it, vi } from 'vitest';
import { ToastService } from '../../../../core/notifications/toast.service';
import { ServiceAccountsApi } from './service-accounts.api';
import { ServiceAccountsStore } from './service-accounts.store';

function setup(api: Partial<Record<keyof ServiceAccountsApi, unknown>>) {
  const toast = { success: vi.fn(), error: vi.fn() };
  const base = { list: vi.fn(() => of([])) };
  TestBed.configureTestingModule({
    providers: [
      ServiceAccountsStore,
      { provide: ServiceAccountsApi, useValue: { ...base, ...api } },
      { provide: ToastService, useValue: toast },
    ],
  });
  return { store: TestBed.inject(ServiceAccountsStore), toast };
}

const account = { id: 's1', code: 'ci', active: true };

describe('ServiceAccountsStore', () => {
  it('carrega contas e reflete vazio', () => {
    const { store } = setup({ list: vi.fn(() => of([])) });
    store.load();
    expect(store.empty()).toBe(true);
  });

  it('emite credencial e guarda o segredo na sessão', () => {
    const { store, toast } = setup({
      issueCredential: vi.fn(() => of({ credentialId: 'c1', rawKey: 'brassia_secret', keyPrefix: 'brassia_s' })),
    });

    store.issueCredential(account, ['scim.read']);

    expect(store.issued()).toHaveLength(1);
    expect(store.issued()[0].rawKey).toBe('brassia_secret');
    expect(store.issued()[0].scopes).toEqual(['scim.read']);
    expect(store.issued()[0].revoked).toBe(false);
    expect(toast.success).toHaveBeenCalled();
  });

  it('marca a credencial como revogada na sessão', () => {
    const { store } = setup({
      issueCredential: vi.fn(() => of({ credentialId: 'c1', rawKey: 'k', keyPrefix: 'p' })),
      revokeCredential: vi.fn(() => of(undefined)),
    });

    store.issueCredential(account, ['s']);
    store.revokeCredential('c1');

    expect(store.issued()[0].revoked).toBe(true);
  });

  it('erro ao criar vira actionError', () => {
    const { store } = setup({ create: vi.fn(() => throwError(() => ({ status: 409 }))) });
    store.create({ code: 'ci', name: 'CI' });
    expect(store.actionError()).not.toBeNull();
    expect(store.submitting()).toBe(false);
  });
});
