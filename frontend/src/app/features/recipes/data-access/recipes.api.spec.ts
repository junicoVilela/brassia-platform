import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { afterEach, describe, expect, it } from 'vitest';
import { RecipesApi } from './recipes.api';

describe('RecipesApi (contrato da jornada frontend↔API)', () => {
  let api: RecipesApi;
  let http: HttpTestingController;

  function setup() {
    TestBed.configureTestingModule({
      providers: [RecipesApi, provideHttpClient(), provideHttpClientTesting()],
    });
    api = TestBed.inject(RecipesApi);
    http = TestBed.inject(HttpTestingController);
  }

  afterEach(() => http.verify());

  it('consulta o endpoint /api/v1/recipes encaminhado pelo proxy', () => {
    setup();
    const summary = { id: '1', name: 'IPA', version: 1, status: 'PUBLISHED', updatedAt: '2026-07-21T00:00:00Z' };
    let received: readonly unknown[] | undefined;
    api.list().subscribe(page => (received = page.content));

    const req = http.expectOne('/api/v1/recipes');
    expect(req.request.method).toBe('GET');
    req.flush({ content: [summary], page: 0, size: 20, totalElements: 1, totalPages: 1 });

    expect(received).toEqual([summary]);
  });
});
