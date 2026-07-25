import { provideHttpClient } from '@angular/common/http';
import { provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { ActivatedRouteSnapshot, Router, RouterStateSnapshot, UrlTree } from '@angular/router';
import { firstValueFrom, of } from 'rxjs';
import { beforeEach, describe, expect, it, vi } from 'vitest';
import { AuthService } from './auth.service';
import { permissionGuard } from './permission.guard';

function route(permission?: string | string[]): ActivatedRouteSnapshot {
  return { data: permission ? { permission } : {} } as unknown as ActivatedRouteSnapshot;
}

function run(auth: Partial<AuthService>, permission?: string | string[]) {
  TestBed.configureTestingModule({
    providers: [{ provide: AuthService, useValue: auth }, provideHttpClient(), provideHttpClientTesting()],
  });
  return TestBed.runInInjectionContext(() =>
    permissionGuard(route(permission), {} as RouterStateSnapshot),
  );
}

describe('permissionGuard', () => {
  beforeEach(() => TestBed.resetTestingModule());

  it('libera quando o principal possui a permissão', async () => {
    const auth = { ensureSession: () => of({} as never), hasAnyPermission: vi.fn(() => true) };
    const result = await firstValueFrom(run(auth, 'security.user.read') as never);
    expect(result).toBe(true);
    expect(auth.hasAnyPermission).toHaveBeenCalledWith(['security.user.read']);
  });

  it('redireciona para /forbidden sem a permissão', async () => {
    const auth = { ensureSession: () => of({} as never), hasAnyPermission: vi.fn(() => false) };
    const result = await firstValueFrom(run(auth, 'security.user.read') as never);
    const tree = TestBed.inject(Router).createUrlTree(['/forbidden']);
    expect(result).toBeInstanceOf(UrlTree);
    expect((result as UrlTree).toString()).toBe(tree.toString());
  });

  it('libera rota sem permissão declarada', async () => {
    const auth = { ensureSession: () => of({} as never), hasAnyPermission: vi.fn(() => false) };
    const result = await firstValueFrom(run(auth) as never);
    expect(result).toBe(true);
  });
});
