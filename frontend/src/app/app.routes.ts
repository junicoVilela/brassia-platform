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
        path: 'experiments',
        canActivate: [permissionGuard],
        data: { permission: 'experiment.plan.read' },
        loadChildren: () =>
          import('./features/experiments/experiments.routes').then(m => m.EXPERIMENTS_ROUTES),
      },
      {
        path: 'field-feedback',
        canActivate: [permissionGuard],
        data: { permission: 'feedback.complaint.read' },
        loadChildren: () =>
          import('./features/field-feedback/field-feedback.routes').then(m => m.FIELD_FEEDBACK_ROUTES),
      },
      {
        path: 'optimization',
        canActivate: [permissionGuard],
        data: { permission: 'optimization.run.read' },
        loadChildren: () =>
          import('./features/optimization/optimization.routes').then(m => m.OPTIMIZATION_ROUTES),
      },
      {
        path: 'blends',
        canActivate: [permissionGuard],
        data: { permission: 'blend.operation.read' },
        loadChildren: () => import('./features/blends/blends.routes').then(m => m.BLENDS_ROUTES),
      },
      {
        path: 'digital-twin',
        canActivate: [permissionGuard],
        data: { permission: 'digitaltwin.profile.read' },
        loadChildren: () =>
          import('./features/digital-twin/digital-twin.routes').then(m => m.DIGITAL_TWIN_ROUTES),
      },
      {
        path: 'fermentation/profiles',
        canActivate: [permissionGuard],
        data: { permission: 'fermentation.profile.read' },
        loadChildren: () => import('./features/fermentation/fermentation.routes').then(m => m.FERMENTATION_ROUTES),
      },
      {
        path: 'fermentation/schedule',
        canActivate: [permissionGuard],
        data: { permission: 'fermentation.schedule.read' },
        loadChildren: () =>
          import('./features/fermentation/fermentation.routes').then(m => m.FERMENTATION_SCHEDULE_ROUTES),
      },
      {
        path: 'fermentation/yeast',
        canActivate: [permissionGuard],
        data: { permission: 'fermentation.yeast.read' },
        loadChildren: () =>
          import('./features/fermentation/fermentation.routes').then(m => m.FERMENTATION_YEAST_ROUTES),
      },
      {
        path: 'fermentation/readings',
        canActivate: [permissionGuard],
        data: { permission: 'fermentation.reading.read' },
        loadChildren: () =>
          import('./features/fermentation/fermentation.routes').then(m => m.FERMENTATION_READING_ROUTES),
      },
      {
        path: 'packaging/plans',
        canActivate: [permissionGuard],
        data: { permission: 'packaging.plan.read' },
        loadChildren: () => import('./features/packaging/packaging.routes').then(m => m.PACKAGING_ROUTES),
      },
      {
        path: 'gas',
        canActivate: [permissionGuard],
        data: { permission: 'gas.read' },
        loadChildren: () => import('./features/gas/gas.routes').then(m => m.GAS_ROUTES),
      },
      {
        path: 'sensory/sessions',
        canActivate: [permissionGuard],
        data: { permission: 'sensory.session.read' },
        loadChildren: () => import('./features/sensory/sensory.routes').then(m => m.SENSORY_ROUTES),
      },
      {
        path: 'quality/control-plans',
        canActivate: [permissionGuard],
        data: { permission: 'quality.plan.read' },
        loadChildren: () => import('./features/quality/quality.routes').then(m => m.QUALITY_ROUTES),
      },
      {
        path: 'metrology/instruments',
        canActivate: [permissionGuard],
        data: { permission: 'metrology.instrument.read' },
        loadChildren: () => import('./features/metrology/metrology.routes').then(m => m.METROLOGY_ROUTES),
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
        path: 'food-safety/allergens',
        canActivate: [permissionGuard],
        data: { permission: 'foodsafety.allergen.read' },
        loadChildren: () =>
          import('./features/food-safety/food-safety.routes').then(m => m.FOOD_SAFETY_ROUTES),
      },
      {
        path: 'traceability/genealogy',
        canActivate: [permissionGuard],
        data: { permission: 'traceability.genealogy.read' },
        loadChildren: () =>
          import('./features/traceability/traceability.routes').then(m => m.TRACEABILITY_ROUTES),
      },
      {
        path: 'packaging/finished-lots',
        canActivate: [permissionGuard],
        data: { permission: 'packaging.plan.read' },
        loadChildren: () =>
          import('./features/packaging/packaging.routes').then(m => m.FINISHED_LOT_ROUTES),
      },
      {
        path: 'traceability/recalls',
        canActivate: [permissionGuard],
        data: { permission: 'traceability.recall.read' },
        loadChildren: () =>
          import('./features/traceability/traceability.routes').then(m => m.RECALL_ROUTES),
      },
      {
        path: 'sales/catalog',
        canActivate: [permissionGuard],
        data: { permission: 'sales.catalog.read' },
        loadChildren: () => import('./features/sales/sales.routes').then(m => m.SALES_ROUTES),
      },
      {
        path: 'portal',
        canActivate: [permissionGuard],
        data: { permission: 'portal.access' },
        loadChildren: () => import('./features/portal/portal.routes').then(m => m.PORTAL_ROUTES),
      },
      {
        path: 'forecast',
        canActivate: [permissionGuard],
        data: { permission: 'forecast.demand.read' },
        loadChildren: () => import('./features/forecast/forecast.routes').then(m => m.FORECAST_ROUTES),
      },
      {
        path: 'sales/orders',
        canActivate: [permissionGuard],
        data: { permission: 'sales.order.read' },
        loadChildren: () => import('./features/sales/sales.routes').then(m => m.ORDER_ROUTES),
      },
      {
        path: 'crm/customers',
        canActivate: [permissionGuard],
        data: { permission: 'crm.customer.read' },
        loadChildren: () => import('./features/crm/crm.routes').then(m => m.CRM_ROUTES),
      },
      {
        path: 'costing/batches',
        canActivate: [permissionGuard],
        data: { permission: 'costing.cost.read' },
        loadChildren: () => import('./features/costing/costing.routes').then(m => m.COSTING_ROUTES),
      },
      {
        path: 'costing/variance',
        canActivate: [permissionGuard],
        data: { permission: 'costing.variance.read' },
        loadChildren: () =>
          import('./features/costing/costing.routes').then(m => m.VARIANCE_ROUTES),
      },
      {
        path: 'reporting/saved-reports',
        canActivate: [permissionGuard],
        data: { permission: 'reporting.saved.read' },
        loadChildren: () =>
          import('./features/reporting/reporting.routes').then(m => m.SAVED_REPORTS_ROUTES),
      },
      {
        path: 'reporting/dashboard',
        canActivate: [permissionGuard],
        data: { permission: 'reporting.dashboard.read' },
        loadChildren: () =>
          import('./features/reporting/reporting.routes').then(m => m.DASHBOARD_ROUTES),
      },
      {
        path: 'reporting/batches',
        canActivate: [permissionGuard],
        data: { permission: 'reporting.batch.read' },
        loadChildren: () =>
          import('./features/reporting/reporting.routes').then(m => m.REPORTING_ROUTES),
      },
      {
        // Sem `permissionGuard`: a alçada depende do TIPO apontado pelo código, que só se conhece depois
        // de interpretá-lo. Quem verifica é o servidor, na resolução — e a tela diz com todas as letras
        // quando falta permissão, em vez de mandar para uma tela vazia.
        path: 'scan',
        loadChildren: () => import('./features/scan/scan.routes').then(m => m.SCAN_ROUTES),
      },
      {
        path: 'integration/webhooks',
        canActivate: [permissionGuard],
        data: { permission: 'integration.webhook.read' },
        loadChildren: () => import('./features/webhooks/webhooks.routes').then(m => m.WEBHOOKS_ROUTES),
      },
      {
        path: 'knowledge',
        canActivate: [permissionGuard],
        data: { permission: 'knowledge.document.read' },
        loadChildren: () =>
          import('./features/knowledge/knowledge.routes').then(m => m.KNOWLEDGE_ROUTES),
      },
      {
        path: 'sensors',
        canActivate: [permissionGuard],
        data: { permission: 'sensor.reading.read' },
        loadChildren: () => import('./features/sensors/sensors.routes').then(m => m.SENSORS_ROUTES),
      },
      {
        path: 'ai/copilot',
        canActivate: [permissionGuard],
        data: { permission: 'ai.answer.ask' },
        loadChildren: () => import('./features/ai/ai.routes').then(m => m.COPILOT_ROUTES),
      },
      {
        path: 'ai/assessments',
        canActivate: [permissionGuard],
        data: { permission: 'ai.assessment.batch' },
        loadChildren: () => import('./features/ai/ai.routes').then(m => m.ASSESSMENT_ROUTES),
      },
      {
        // `ai.command.read` e não `ai.command.propose`: a tela serve para decidir e para auditar, e quem
        // confirma não é necessariamente quem pede a proposta.
        path: 'ai/proposals',
        canActivate: [permissionGuard],
        data: { permission: 'ai.command.read' },
        loadChildren: () => import('./features/ai/ai.routes').then(m => m.PROPOSAL_ROUTES),
      },
      {
        path: 'ai/gateway',
        canActivate: [permissionGuard],
        data: { permission: 'ai.gateway.read' },
        loadChildren: () => import('./features/ai/ai.routes').then(m => m.AI_ROUTES),
      },
      {
        path: 'utilities/indicators',
        canActivate: [permissionGuard],
        data: { permission: 'utilities.indicator.read' },
        loadChildren: () =>
          import('./features/utilities/utilities.routes').then(m => m.UTILITIES_ROUTES),
      },
      {
        path: 'traceability/recall-drills',
        canActivate: [permissionGuard],
        data: { permission: 'traceability.drill.read' },
        loadChildren: () =>
          import('./features/traceability/traceability.routes').then(m => m.DRILL_ROUTES),
      },
      {
        path: 'traceability/quarantines',
        canActivate: [permissionGuard],
        data: { permission: 'traceability.quarantine.read' },
        loadChildren: () =>
          import('./features/traceability/traceability.routes').then(m => m.QUARANTINE_ROUTES),
      },
      {
        path: 'settings/parameters',
        canActivate: [permissionGuard],
        // Ler os parâmetros basta ler qualquer um dos módulos que eles regem.
        data: {
          permission: [
            'sanitation.cycle.read',
            'gas.read',
            'metrology.instrument.read',
            'quality.nc.read',
            'sensory.session.read',
          ],
        },
        loadChildren: () => import('./features/parameters/parameters.routes').then(m => m.PARAMETERS_ROUTES),
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
