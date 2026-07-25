import { TestBed } from '@angular/core/testing';
import { of, throwError } from 'rxjs';
import { describe, expect, it, vi } from 'vitest';
import { ToastService } from '../../../../core/notifications/toast.service';
import { FederationApi } from './federation.api';
import { FederationStore } from './federation.store';

function setup(api: Partial<Record<keyof FederationApi, unknown>>) {
  const toast = { success: vi.fn(), error: vi.fn() };
  const base = {
    list: vi.fn(() => of([])),
    listIdentities: vi.fn(() => of([])),
    listScimMappings: vi.fn(() => of([])),
    listGroups: vi.fn(() => of([])),
  };
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

  const provider = { id: 'p1', code: 'okta', displayName: 'Okta', protocol: 'OIDC', status: 'VALIDATED', issuerOrEntityId: 'i', metadataUri: null, jitMode: false, version: 0 } as const;

  it('seleciona provedor e carrega identidades, mapeamentos e grupos', () => {
    const listIdentities = vi.fn(() => of([{ userId: 'u1', externalSubject: 'okta|1', normalizedEmail: null, linkedAt: 'x' }]));
    const listScimMappings = vi.fn(() => of([{ externalGroupId: 'idp-admins', securityGroupId: 'g1', active: true }]));
    const { store } = setup({
      listIdentities,
      listScimMappings,
      listGroups: vi.fn(() => of([{ id: 'g1', name: 'Admin' }])),
    });

    store.selectProvider({ ...provider });

    expect(listIdentities).toHaveBeenCalledWith('p1');
    expect(store.identities()).toHaveLength(1);
    expect(store.mappings()).toHaveLength(1);
    expect(store.groupName('g1')).toBe('Admin');
    expect(store.selected()?.id).toBe('p1');
  });

  it('upsert de mapeamento recarrega a lista', () => {
    const listScimMappings = vi.fn(() => of([]));
    const { store, toast } = setup({ listScimMappings, upsertScimMapping: vi.fn(() => of(undefined)) });

    store.selectProvider({ ...provider });
    store.upsertMapping('idp-admins', 'g1');

    expect(toast.success).toHaveBeenCalled();
    expect(listScimMappings).toHaveBeenCalledTimes(2);
  });
});
