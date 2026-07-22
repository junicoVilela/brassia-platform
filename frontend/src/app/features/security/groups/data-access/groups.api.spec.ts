import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { afterEach, beforeEach, describe, expect, it } from 'vitest';
import { GroupsApi } from './groups.api';

describe('GroupsApi', () => {
  let api: GroupsApi;
  let http: HttpTestingController;

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [GroupsApi, provideHttpClient(), provideHttpClientTesting()],
    });
    api = TestBed.inject(GroupsApi);
    http = TestBed.inject(HttpTestingController);
  });

  afterEach(() => http.verify());

  it('lista grupos e permissões', () => {
    api.listGroups().subscribe();
    http.expectOne('/api/v1/security/groups').flush([]);

    api.listPermissions().subscribe();
    http.expectOne('/api/v1/security/permissions').flush([]);
  });

  it('cria e atualiza grupo', () => {
    api.create({ code: 'BREWERS', name: 'Cervejeiros', permissionCodes: ['recipe.create'] }).subscribe();
    const create = http.expectOne('/api/v1/security/groups');
    expect(create.request.method).toBe('POST');
    create.flush({
      id: '1', code: 'BREWERS', name: 'Cervejeiros', description: null, breweryId: 'b',
      systemGroup: false, active: true, version: 0, permissions: ['recipe.create'],
    });

    api.update('1', { name: 'Cervejeiros', permissionCodes: [], version: 0 }).subscribe();
    const update = http.expectOne('/api/v1/security/groups/1');
    expect(update.request.method).toBe('PATCH');
    update.flush({
      id: '1', code: 'BREWERS', name: 'Cervejeiros', description: null, breweryId: 'b',
      systemGroup: false, active: true, version: 1, permissions: [],
    });
  });
});
