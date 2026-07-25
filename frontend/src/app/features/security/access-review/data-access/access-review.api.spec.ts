import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { afterEach, beforeEach, describe, expect, it } from 'vitest';
import { AccessReviewApi } from './access-review.api';

describe('AccessReviewApi', () => {
  let api: AccessReviewApi;
  let http: HttpTestingController;

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [AccessReviewApi, provideHttpClient(), provideHttpClientTesting()],
    });
    api = TestBed.inject(AccessReviewApi);
    http = TestBed.inject(HttpTestingController);
  });

  afterEach(() => http.verify());

  it('cria revisão e lê itens', () => {
    api.createReview({ name: 'Q3', dueAt: '2026-09-01T00:00:00Z' }).subscribe();
    const post = http.expectOne(r => r.method === 'POST' && r.url === '/api/v1/security/access-reviews');
    expect(post.request.body).toEqual({ name: 'Q3', dueAt: '2026-09-01T00:00:00Z' });
    post.flush({ id: 'r1' });

    let items: unknown;
    api.listItems('r1').subscribe(i => (items = i));
    http.expectOne('/api/v1/security/access-reviews/r1/items')
      .flush([{ id: 'i1', userId: 'u1', groupId: 'g1', decision: 'PENDING' }]);
    expect(items).toHaveLength(1);
  });

  it('decide item com decisão e justificativa', () => {
    api.decideItem('i1', 'REMOVE', 'excesso de acesso').subscribe();
    const req = http.expectOne('/api/v1/security/access-reviews/items/i1/decide');
    expect(req.request.method).toBe('POST');
    expect(req.request.body).toEqual({ decision: 'REMOVE', justification: 'excesso de acesso' });
    req.flush(null);
  });

  it('cria e lista regras de segregação', () => {
    api.createRule({ leftPermissionCode: 'a', rightPermissionCode: 'b', reason: 'r' }).subscribe();
    const post = http.expectOne(r => r.method === 'POST' && r.url === '/api/v1/security/segregation-rules');
    expect(post.request.body).toEqual({ leftPermissionCode: 'a', rightPermissionCode: 'b', reason: 'r' });
    post.flush({ id: 's1' });

    let rules: unknown;
    api.listRules().subscribe(r => (rules = r));
    http.expectOne('/api/v1/security/segregation-rules')
      .flush([{ id: 's1', leftPermissionCode: 'a', rightPermissionCode: 'b', reason: 'r', active: true }]);
    expect(rules).toHaveLength(1);
  });

  it('normaliza catálogos de usuários, grupos e permissões', () => {
    let users: unknown;
    api.listUsers().subscribe(u => (users = u));
    http.expectOne(r => r.url === '/api/v1/security/users').flush({ content: [{ id: 'u1', displayName: 'Ana' }] });
    expect(users).toEqual([{ id: 'u1', name: 'Ana' }]);

    let groups: unknown;
    api.listGroups().subscribe(g => (groups = g));
    http.expectOne('/api/v1/security/groups').flush([{ id: 'g1', name: 'Admin' }]);
    expect(groups).toEqual([{ id: 'g1', name: 'Admin' }]);

    let perms: unknown;
    api.listPermissions().subscribe(p => (perms = p));
    http.expectOne('/api/v1/security/permissions').flush([{ code: 'recipe.read' }]);
    expect(perms).toEqual(['recipe.read']);
  });
});
