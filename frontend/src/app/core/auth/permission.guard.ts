import { inject } from '@angular/core';
import { CanActivateFn, Router } from '@angular/router';
import { map } from 'rxjs';
import { AuthService } from './auth.service';

/**
 * Guard reutilizável de autorização: exige a(s) permissão(ões) declarada(s) em
 * `route.data.permission` (string ou string[]). Sem a permissão, redireciona
 * para /forbidden em vez de deixar a tela carregar e quebrar.
 */
export const permissionGuard: CanActivateFn = route => {
  const auth = inject(AuthService);
  const router = inject(Router);
  const required = route.data['permission'] as string | string[] | undefined;

  return auth.ensureSession().pipe(
    map(() => {
      if (!required) {
        return true;
      }
      const permissions = Array.isArray(required) ? required : [required];
      return auth.hasAnyPermission(permissions) ? true : router.createUrlTree(['/forbidden']);
    }),
  );
};
