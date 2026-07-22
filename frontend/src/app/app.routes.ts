import { Routes } from '@angular/router';
import { authGuard } from './core/auth/auth.guard';
import { LoginPageComponent } from './features/auth/login-page/login-page.component';
import { ShellComponent } from './layout/shell.component';

export const routes: Routes = [
  { path: 'login', component: LoginPageComponent },
  {
    path: '',
    component: ShellComponent,
    canActivate: [authGuard],
    children: [
      {
        path: 'recipes',
        loadChildren: () => import('./features/recipes/recipes.routes').then(m => m.RECIPE_ROUTES),
      },
      {
        path: 'breweries',
        loadChildren: () => import('./features/brewery/brewery.routes').then(m => m.BREWERY_ROUTES),
      },
      {
        path: 'catalog',
        loadChildren: () => import('./features/catalog/catalog.routes').then(m => m.CATALOG_ROUTES),
      },
      {
        path: 'security/users',
        loadChildren: () =>
          import('./features/security/users/security-users.routes').then(m => m.SECURITY_USERS_ROUTES),
      },
      {
        path: 'security/groups',
        loadChildren: () =>
          import('./features/security/groups/security-groups.routes').then(m => m.SECURITY_GROUPS_ROUTES),
      },
      { path: '', pathMatch: 'full', redirectTo: 'recipes' },
    ],
  },
];
