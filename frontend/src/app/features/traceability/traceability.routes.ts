import { Routes } from '@angular/router';
import { GenealogyPageComponent } from './pages/genealogy-page/genealogy-page.component';
import { QuarantinesPageComponent } from './pages/quarantines-page/quarantines-page.component';

export const TRACEABILITY_ROUTES: Routes = [{ path: '', component: GenealogyPageComponent }];

export const QUARANTINE_ROUTES: Routes = [{ path: '', component: QuarantinesPageComponent }];
