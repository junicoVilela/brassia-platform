import { Routes } from '@angular/router';
import { EquipmentPageComponent } from './pages/equipment-page/equipment-page.component';
import { MaintenancePageComponent } from './pages/maintenance-page/maintenance-page.component';

export const EQUIPMENT_ROUTES: Routes = [
  { path: '', component: EquipmentPageComponent },
  { path: 'maintenance', component: MaintenancePageComponent },
];
