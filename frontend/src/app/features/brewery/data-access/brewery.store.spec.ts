import { TestBed } from '@angular/core/testing';
import { of, throwError } from 'rxjs';
import { describe, expect, it, vi } from 'vitest';
import { BreweryApi } from './brewery.api';
import { BreweryStore } from './brewery.store';

function page(content: unknown[]) {
  return of({ content, page: 0, size: 20, totalElements: content.length, totalPages: 1 });
}

describe('BreweryStore', () => {
  it('carrega e reflete estado vazio', () => {
    const api = { list: vi.fn(() => page([])) };
    TestBed.configureTestingModule({ providers: [BreweryStore, { provide: BreweryApi, useValue: api }] });
    const store = TestBed.inject(BreweryStore);

    store.load();

    expect(api.list).toHaveBeenCalledOnce();
    expect(store.empty()).toBe(true);
  });

  it('cadastra e recarrega no sucesso', () => {
    const api = {
      list: vi.fn(() => page([{ id: '1', code: 'SB40', name: 'Casa', timezone: 'America/Sao_Paulo' }])),
      register: vi.fn(() => of({ id: '1', code: 'SB40', name: 'Casa', timezone: 'America/Sao_Paulo' })),
    };
    TestBed.configureTestingModule({ providers: [BreweryStore, { provide: BreweryApi, useValue: api }] });
    const store = TestBed.inject(BreweryStore);

    const onSuccess = vi.fn();
    store.register({ code: 'SB40', name: 'Casa', timezone: 'America/Sao_Paulo' }, onSuccess);

    expect(api.register).toHaveBeenCalledOnce();
    expect(onSuccess).toHaveBeenCalledOnce();
    expect(store.items()).toHaveLength(1);
  });

  it('marca actionError quando o cadastro falha', () => {
    const api = {
      list: vi.fn(() => page([])),
      register: vi.fn(() => throwError(() => ({ status: 409 }))),
    };
    TestBed.configureTestingModule({ providers: [BreweryStore, { provide: BreweryApi, useValue: api }] });
    const store = TestBed.inject(BreweryStore);

    store.register({ code: 'SB40', name: 'Casa', timezone: 'America/Sao_Paulo' });

    expect(store.actionError()).not.toBeNull();
    expect(store.submitting()).toBe(false);
  });
});
