import { Routes } from '@angular/router';
import { ShellComponent } from './layout/shell.component';

export const routes: Routes = [
  {
    path: '',
    component: ShellComponent,
    children: [
      {
        path: 'recipes',
        loadChildren: () => import('./features/recipes/recipes.routes').then(m => m.RECIPE_ROUTES),
      },
      {
        path: 'security/users',
        loadChildren: () =>
          import('./features/security/users/security-users.routes').then(m => m.SECURITY_USERS_ROUTES),
      },
      { path: '', pathMatch: 'full', redirectTo: 'recipes' },
    ],
  },
];
