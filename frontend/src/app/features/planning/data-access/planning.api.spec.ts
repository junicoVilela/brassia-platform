import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { afterEach, describe, expect, it } from 'vitest';
import { PlanningApi } from './planning.api';

describe('PlanningApi (contrato da agenda de produção)', () => {
  let api: PlanningApi;
  let http: HttpTestingController;

  function setup() {
    TestBed.configureTestingModule({
      providers: [PlanningApi, provideHttpClient(), provideHttpClientTesting()],
    });
    api = TestBed.inject(PlanningApi);
    http = TestBed.inject(HttpTestingController);
  }

  afterEach(() => http.verify());

  it('lista a agenda por período', () => {
    setup();
    let received: unknown;
    api.list('2026-08-01T00:00:00Z', '2026-08-02T00:00:00Z').subscribe(entries => (received = entries));

    const req = http.expectOne(r => r.url === '/api/v1/planning/schedule');
    expect(req.request.method).toBe('GET');
    expect(req.request.params.get('from')).toBe('2026-08-01T00:00:00Z');
    expect(req.request.params.get('to')).toBe('2026-08-02T00:00:00Z');
    req.flush([]);
    expect(received).toEqual([]);
  });

  it('simula sem persistir (POST /simulate)', () => {
    setup();
    let result: unknown;
    api.simulate({ equipmentId: 'eq', scheduledStart: 's', scheduledEnd: 'e' }).subscribe(r => (result = r));

    const req = http.expectOne('/api/v1/planning/schedule/simulate');
    expect(req.request.method).toBe('POST');
    req.flush({ hasConflict: true, conflicts: [] });
    expect(result).toEqual({ hasConflict: true, conflicts: [] });
  });

  it('cria uma entrada (POST)', () => {
    setup();
    api.create({
      recipeId: 'r', equipmentId: 'eq', assignedUserId: 'u', plannedVolumeLiters: 40,
      scheduledStart: 's', scheduledEnd: 'e',
    }).subscribe();

    const req = http.expectOne('/api/v1/planning/schedule');
    expect(req.request.method).toBe('POST');
    req.flush({ id: 'x', status: 'PLANNED' });
  });
});
