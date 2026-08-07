import { Routes } from '@angular/router';
import { BatchCostsPageComponent } from './pages/batch-costs-page/batch-costs-page.component';
import { BatchVariancePageComponent } from './pages/batch-variance-page/batch-variance-page.component';

export const COSTING_ROUTES: Routes = [{ path: '', component: BatchCostsPageComponent }];

export const VARIANCE_ROUTES: Routes = [{ path: '', component: BatchVariancePageComponent }];
