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
        path: 'planning',
        canActivate: [permissionGuard],
        data: { permission: 'planning.schedule.read' },
        loadChildren: () => import('./features/planning/planning.routes').then(m => m.PLANNING_ROUTES),
      },
      {
        path: 'brew-orders',
        canActivate: [permissionGuard],
        data: { permission: 'planning.order.read' },
        loadChildren: () => import('./features/orders/orders.routes').then(m => m.ORDERS_ROUTES),
      },
      {
        path: 'inventory',
        canActivate: [permissionGuard],
        data: { permission: 'inventory.lot.read' },
        loadChildren: () => import('./features/inventory/inventory.routes').then(m => m.INVENTORY_ROUTES),
      },
      {
        path: 'production/batches',
        canActivate: [permissionGuard],
        data: { permission: 'production.batch.read' },
        loadChildren: () => import('./features/production/production.routes').then(m => m.PRODUCTION_ROUTES),
      },
      {
        path: 'sanitation/procedures',
        canActivate: [permissionGuard],
        data: { permission: 'sanitation.procedure.read' },
        loadChildren: () => import('./features/sanitation/sanitation.routes').then(m => m.SANITATION_ROUTES),
      },
      {
        path: 'fermentation/profiles',
        canActivate: [permissionGuard],
        data: { permission: 'fermentation.profile.read' },
        loadChildren: () => import('./features/fermentation/fermentation.routes').then(m => m.FERMENTATION_ROUTES),
      },
      {
        path: 'fermentation/readings',
        canActivate: [permissionGuard],
        data: { permission: 'fermentation.reading.read' },
        loadChildren: () =>
          import('./features/fermentation/fermentation.routes').then(m => m.FERMENTATION_READING_ROUTES),
      },
      {
        path: 'sanitation/matrix',
        canActivate: [permissionGuard],
        data: { permission: 'sanitation.matrix.read' },
        loadChildren: () => import('./features/sanitation/sanitation.routes').then(m => m.SANITATION_MATRIX_ROUTES),
      },
      {
        path: 'sanitation/cycles',
        canActivate: [permissionGuard],
        data: { permission: 'sanitation.cycle.read' },
        loadChildren: () => import('./features/sanitation/sanitation.routes').then(m => m.SANITATION_CYCLE_ROUTES),
      },
      {
        path: 'suppliers',
        canActivate: [permissionGuard],
        data: { permission: 'purchasing.supplier.read' },
        loadChildren: () => import('./features/purchasing/purchasing.routes').then(m => m.PURCHASING_ROUTES),
      },
      {
        path: 'purchasing/needs',
        canActivate: [permissionGuard],
        data: { permission: 'purchasing.purchase.read' },
        loadComponent: () =>
          import('./features/purchasing/pages/purchase-needs-page/purchase-needs-page.component')
            .then(m => m.PurchaseNeedsPageComponent),
      },
      {
        path: 'purchasing/shopping-list',
        canActivate: [permissionGuard],
        data: { permission: 'purchasing.purchase.read' },
        loadComponent: () =>
          import('./features/purchasing/pages/shopping-list-page/shopping-list-page.component')
            .then(m => m.ShoppingListPageComponent),
      },
      {
        path: 'account',
        loadChildren: () => import('./features/account/account.routes').then(m => m.ACCOUNT_ROUTES),
      },
      {
        path: 'settings',
        loadChildren: () => import('./features/settings/settings.routes').then(m => m.SETTINGS_ROUTES),
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
      {
        path: 'security/temporary-access',
        canActivate: [permissionGuard],
        data: { permission: 'security.temporary-access.read' },
        loadChildren: () =>
          import('./features/security/temporary-access/temporary-access.routes').then(
            m => m.TEMPORARY_ACCESS_ROUTES,
          ),
      },
      {
        path: 'security/access-review',
        canActivate: [permissionGuard],
        data: { permission: ['security.access-review.read', 'security.segregation.manage'] },
        loadChildren: () =>
          import('./features/security/access-review/access-review.routes').then(m => m.ACCESS_REVIEW_ROUTES),
      },
      {
        path: 'security/alerts',
        canActivate: [permissionGuard],
        data: { permission: 'security.alert.read' },
        loadChildren: () =>
          import('./features/security/alerts/alerts.routes').then(m => m.ALERTS_ROUTES),
      },
      {
        path: 'security/audit',
        canActivate: [permissionGuard],
        data: { permission: 'security.audit.read' },
        loadChildren: () =>
          import('./features/security/audit/audit.routes').then(m => m.AUDIT_ROUTES),
      },
      {
        path: 'security/service-accounts',
        canActivate: [permissionGuard],
        data: { permission: 'security.service-account.read' },
        loadChildren: () =>
          import('./features/security/service-accounts/service-accounts.routes').then(
            m => m.SERVICE_ACCOUNTS_ROUTES,
          ),
      },
      {
        path: 'security/federation',
        canActivate: [permissionGuard],
        data: { permission: 'security.federation.read' },
        loadChildren: () =>
          import('./features/security/federation/federation.routes').then(m => m.FEDERATION_ROUTES),
      },
      { path: 'forbidden', component: ForbiddenPageComponent },
      { path: '', pathMatch: 'full', redirectTo: 'recipes' },
    ],
  },
];
