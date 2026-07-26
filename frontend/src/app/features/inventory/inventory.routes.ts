import { Routes } from '@angular/router';
import { permissionGuard } from '../../core/auth/permission.guard';
import { CountsPageComponent } from './pages/counts-page/counts-page.component';
import { InventoryPageComponent } from './pages/inventory-page/inventory-page.component';

export const INVENTORY_ROUTES: Routes = [
  { path: '', component: InventoryPageComponent },
  {
    path: 'counts',
    component: CountsPageComponent,
    canActivate: [permissionGuard],
    data: { permission: 'inventory.count.read' },
  },
];
