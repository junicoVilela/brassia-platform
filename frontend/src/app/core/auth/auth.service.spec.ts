import { TestBed } from '@angular/core/testing';
import { of } from 'rxjs';
import { describe, expect, it } from 'vitest';
import { AuthApi } from './auth.api';
import { AuthService } from './auth.service';
import { SessionUser } from './session-user.model';

function sessionUser(permissions: string[]): SessionUser {
  return { userId: '1', displayName: 'Ana', activeBrewery: null, accessibleBreweries: [], permissions };
}

function setup(permissions: string[]) {
  const api = { session: () => of(sessionUser(permissions)) };
  TestBed.configureTestingModule({ providers: [AuthService, { provide: AuthApi, useValue: api }] });
  const auth = TestBed.inject(AuthService);
  auth.ensureSession().subscribe();
  return auth;
}

describe('AuthService.hasPermission', () => {
  it('reconhece uma permissão presente no principal', () => {
    const auth = setup(['security.user.read', 'recipe.read']);
    expect(auth.hasPermission('security.user.read')).toBe(true);
    expect(auth.hasPermission('security.group.read')).toBe(false);
  });

  it('hasAnyPermission exige ao menos uma das permissões', () => {
    const auth = setup(['security.group.read']);
    expect(auth.hasAnyPermission(['security.user.read', 'security.group.read'])).toBe(true);
    expect(auth.hasAnyPermission(['security.user.read', 'security.audit.read'])).toBe(false);
  });

  it('sem sessão, nenhuma permissão é concedida', () => {
    TestBed.configureTestingModule({
      providers: [AuthService, { provide: AuthApi, useValue: { session: () => of(null) } }],
    });
    const auth = TestBed.inject(AuthService);
    expect(auth.hasPermission('security.user.read')).toBe(false);
  });
});
