import { inject } from '@angular/core';
import { CanActivateFn, Router } from '@angular/router';
import { map } from 'rxjs';
import { AuthService } from './auth.service';

/** Protege rotas do shell: redireciona para /login quando não autenticado. */
export const authGuard: CanActivateFn = (_route, state) => {
  const auth = inject(AuthService);
  const router = inject(Router);

  return auth.ensureSession().pipe(
    map(user =>
      user ? true : router.createUrlTree(['/login'], { queryParams: { returnUrl: state.url } }),
    ),
  );
};
