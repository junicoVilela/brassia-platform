import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { afterEach, beforeEach, describe, expect, it } from 'vitest';
import { BreweryApi } from './brewery.api';

describe('BreweryApi', () => {
  let api: BreweryApi;
  let http: HttpTestingController;

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [BreweryApi, provideHttpClient(), provideHttpClientTesting()],
    });
    api = TestBed.inject(BreweryApi);
    http = TestBed.inject(HttpTestingController);
  });

  afterEach(() => http.verify());

  it('lista cervejarias paginadas', () => {
    let content: unknown;
    api.list().subscribe(page => (content = page.content));

    const req = http.expectOne(r => r.url === '/api/v1/breweries');
    expect(req.request.method).toBe('GET');
    req.flush({ content: [{ id: '1', code: 'SB40', name: 'Casa', timezone: 'America/Sao_Paulo' }],
               page: 0, size: 20, totalElements: 1, totalPages: 1 });

    expect(content).toHaveLength(1);
  });

  it('cadastra via POST', () => {
    api.register({ code: 'SB40', name: 'Casa', timezone: 'America/Sao_Paulo' }).subscribe();
    const req = http.expectOne('/api/v1/breweries');
    expect(req.request.method).toBe('POST');
    expect(req.request.body).toEqual({ code: 'SB40', name: 'Casa', timezone: 'America/Sao_Paulo' });
    req.flush({ id: '1', code: 'SB40', name: 'Casa', timezone: 'America/Sao_Paulo' });
  });
});
