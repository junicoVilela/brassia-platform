import { Routes } from '@angular/router';
import { MatrixPageComponent } from './pages/matrix-page/matrix-page.component';
import { ProceduresPageComponent } from './pages/procedures-page/procedures-page.component';

export const SANITATION_ROUTES: Routes = [{ path: '', component: ProceduresPageComponent }];

export const SANITATION_MATRIX_ROUTES: Routes = [{ path: '', component: MatrixPageComponent }];
