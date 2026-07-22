import { TestBed } from '@angular/core/testing';
import { of, throwError } from 'rxjs';
import { describe, expect, it, vi } from 'vitest';
import { BreweryApi } from './brewery.api';
import { BreweryStore } from './brewery.store';

function page(content: unknown[]) {
  return of({ content, page: 0, size: 20, totalElements: content.length, totalPages: 1 });
}

const defaultPreferences = {
  breweryId: 'b',
  volumeUnit: 'L',
  massUnit: 'KG',
  temperatureUnit: 'C',
  currencyCode: 'BRL',
  maxBatchVolume: 1000,
  allowNegativeStock: false,
  stockPolicy: 'FEFO',
  version: 0,
};

function apiMock(overrides: Record<string, unknown> = {}) {
  return {
    list: vi.fn(() => page([])),
    getPreferences: vi.fn(() => of(defaultPreferences)),
    updatePreferences: vi.fn(() => of({ ...defaultPreferences, version: 1 })),
    register: vi.fn(() => of({ id: '1', code: 'SB40', name: 'Casa', timezone: 'America/Sao_Paulo' })),
    ...overrides,
  };
}

describe('BreweryStore', () => {
  it('carrega e reflete estado vazio', () => {
    const api = apiMock();
    TestBed.configureTestingModule({ providers: [BreweryStore, { provide: BreweryApi, useValue: api }] });
    const store = TestBed.inject(BreweryStore);

    store.load();

    expect(api.list).toHaveBeenCalledOnce();
    expect(api.getPreferences).toHaveBeenCalledOnce();
    expect(store.empty()).toBe(true);
    expect(store.preferences()?.currencyCode).toBe('BRL');
  });

  it('cadastra e recarrega no sucesso', () => {
    const api = apiMock({
      list: vi.fn(() => page([{ id: '1', code: 'SB40', name: 'Casa', timezone: 'America/Sao_Paulo' }])),
    });
    TestBed.configureTestingModule({ providers: [BreweryStore, { provide: BreweryApi, useValue: api }] });
    const store = TestBed.inject(BreweryStore);

    const onSuccess = vi.fn();
    store.register({ code: 'SB40', name: 'Casa', timezone: 'America/Sao_Paulo' }, onSuccess);

    expect(api.register).toHaveBeenCalledOnce();
    expect(onSuccess).toHaveBeenCalledOnce();
    expect(store.items()).toHaveLength(1);
  });

  it('marca actionError quando o cadastro falha', () => {
    const api = apiMock({
      register: vi.fn(() => throwError(() => ({ status: 409 }))),
    });
    TestBed.configureTestingModule({ providers: [BreweryStore, { provide: BreweryApi, useValue: api }] });
    const store = TestBed.inject(BreweryStore);

    store.register({ code: 'SB40', name: 'Casa', timezone: 'America/Sao_Paulo' });

    expect(store.actionError()).not.toBeNull();
    expect(store.submitting()).toBe(false);
  });

  it('salva preferências e atualiza o estado', () => {
    const api = apiMock({
      updatePreferences: vi.fn(() => of({ ...defaultPreferences, currencyCode: 'USD', version: 1 })),
    });
    TestBed.configureTestingModule({ providers: [BreweryStore, { provide: BreweryApi, useValue: api }] });
    const store = TestBed.inject(BreweryStore);

    store.savePreferences({
      volumeUnit: 'L',
      massUnit: 'KG',
      temperatureUnit: 'C',
      currencyCode: 'USD',
      maxBatchVolume: 1000,
      allowNegativeStock: false,
      stockPolicy: 'FEFO',
      version: 0,
    });

    expect(api.updatePreferences).toHaveBeenCalledOnce();
    expect(store.preferences()?.currencyCode).toBe('USD');
    expect(store.preferences()?.version).toBe(1);
  });
});
