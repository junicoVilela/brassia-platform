import { Routes } from '@angular/router';
import { authGuard } from './core/auth/auth.guard';
import { permissionGuard } from './core/auth/permission.guard';
import { ForgotPasswordPageComponent } from './features/auth/forgot-password-page/forgot-password-page.component';
import { LoginPageComponent } from './features/auth/login-page/login-page.component';
import { ResetPasswordPageComponent } from './features/auth/reset-password-page/reset-password-page.component';
import { VerifyEmailPageComponent } from './features/auth/verify-email-page/verify-email-page.component';
import { ForbiddenPageComponent } from './features/errors/forbidden-page/forbidden-page.component';
import { ShellComponent } from './layout/shell.component';

export const routes: Routes = [
  { path: 'login', component: LoginPageComponent },
  { path: 'forgot-password', component: ForgotPasswordPageComponent },
  { path: 'reset-password', component: ResetPasswordPageComponent },
  { path: 'verify-email', component: VerifyEmailPageComponent },
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
        path: 'equipment',
        loadChildren: () => import('./features/equipment/equipment.routes').then(m => m.EQUIPMENT_ROUTES),
      },
      {
        path: 'water',
        loadChildren: () => import('./features/water/water.routes').then(m => m.WATER_ROUTES),
      },
      {
        path: 'reference',
        loadChildren: () => import('./features/reference/reference.routes').then(m => m.REFERENCE_ROUTES),
      },
      {
        path: 'calculators',
        loadChildren: () => import('./features/calculators/calculators.routes').then(m => m.CALCULATORS_ROUTES),
      },
      {
        path: 'account',
        loadChildren: () => import('./features/account/account.routes').then(m => m.ACCOUNT_ROUTES),
      },
      {
        path: 'security/users',
        canActivate: [permissionGuard],
        data: { permission: 'security.user.read' },
        loadChildren: () =>
          import('./features/security/users/security-users.routes').then(m => m.SECURITY_USERS_ROUTES),
      },
      {
        path: 'security/groups',
        canActivate: [permissionGuard],
        data: { permission: ['security.group.read', 'security.permission.read'] },
        loadChildren: () =>
          import('./features/security/groups/security-groups.routes').then(m => m.SECURITY_GROUPS_ROUTES),
      },
      { path: 'forbidden', component: ForbiddenPageComponent },
      { path: '', pathMatch: 'full', redirectTo: 'recipes' },
    ],
  },
];
