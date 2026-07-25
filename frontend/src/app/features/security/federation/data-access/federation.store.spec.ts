import { TestBed } from '@angular/core/testing';
import { of, throwError } from 'rxjs';
import { describe, expect, it, vi } from 'vitest';
import { ToastService } from '../../../../core/notifications/toast.service';
import { FederationApi } from './federation.api';
import { FederationStore } from './federation.store';

function setup(api: Partial<Record<keyof FederationApi, unknown>>) {
  const toast = { success: vi.fn(), error: vi.fn() };
  const base = { list: vi.fn(() => of([])) };
  TestBed.configureTestingModule({
    providers: [
      FederationStore,
      { provide: FederationApi, useValue: { ...base, ...api } },
      { provide: ToastService, useValue: toast },
    ],
  });
  return { store: TestBed.inject(FederationStore), toast };
}

const body = {
  code: 'okta', displayName: 'Okta', protocol: 'OIDC' as const,
  issuerOrEntityId: 'https://idp', configuration: {},
};

describe('FederationStore', () => {
  it('carrega provedores e reflete vazio', () => {
    const { store } = setup({ list: vi.fn(() => of([])) });
    store.load();
    expect(store.empty()).toBe(true);
  });

  it('cria e recarrega no sucesso', () => {
    const list = vi.fn(() => of([]));
    const { store, toast } = setup({ list, create: vi.fn(() => of({ id: 'p1' })) });
    const onSuccess = vi.fn();

    store.create(body, onSuccess);

    expect(onSuccess).toHaveBeenCalledOnce();
    expect(toast.success).toHaveBeenCalledOnce();
    expect(list).toHaveBeenCalledOnce();
  });

  it('valida e recarrega', () => {
    const list = vi.fn(() => of([]));
    const { store, toast } = setup({ list, validate: vi.fn(() => of(undefined)) });

    store.validate('p1');

    expect(toast.success).toHaveBeenCalledOnce();
    expect(list).toHaveBeenCalledOnce();
  });

  it('erro de validação vira actionError', () => {
    const { store } = setup({ validate: vi.fn(() => throwError(() => ({ status: 422 }))) });
    store.validate('p1');
    expect(store.actionError()).not.toBeNull();
  });
});
