import { Routes } from '@angular/router';
import { BatchReportPageComponent } from './pages/batch-report-page/batch-report-page.component';
import { DashboardPageComponent } from './pages/dashboard-page/dashboard-page.component';
import { SavedReportsPageComponent } from './pages/saved-reports-page/saved-reports-page.component';

export const REPORTING_ROUTES: Routes = [{ path: '', component: BatchReportPageComponent }];

export const DASHBOARD_ROUTES: Routes = [{ path: '', component: DashboardPageComponent }];

export const SAVED_REPORTS_ROUTES: Routes = [{ path: '', component: SavedReportsPageComponent }];
