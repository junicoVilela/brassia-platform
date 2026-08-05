import { Routes } from '@angular/router';
import { FinishedLotsPageComponent } from './pages/finished-lots-page/finished-lots-page.component';
import { PlansPageComponent } from './pages/plans-page/plans-page.component';

export const PACKAGING_ROUTES: Routes = [{ path: '', component: PlansPageComponent }];

export const FINISHED_LOT_ROUTES: Routes = [{ path: '', component: FinishedLotsPageComponent }];
