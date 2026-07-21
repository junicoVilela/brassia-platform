import { TestBed } from '@angular/core/testing';
import { of } from 'rxjs';
import { describe, expect, it, vi } from 'vitest';
import { RecipesApi } from './recipes.api';
import { RecipesStore } from './recipes.store';

describe('RecipesStore', () => {
  it('loads recipes', () => {
    const api = { list: vi.fn(() => of({ content: [], page: 0, size: 20, totalElements: 0, totalPages: 0 })) };
    TestBed.configureTestingModule({ providers: [RecipesStore, { provide: RecipesApi, useValue: api }] });
    const store = TestBed.inject(RecipesStore);
    store.load();
    expect(api.list).toHaveBeenCalledOnce();
    expect(store.empty()).toBe(true);
  });
});
