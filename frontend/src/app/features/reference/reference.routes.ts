import { Routes } from '@angular/router';
import { ReferencePageComponent } from './pages/reference-page/reference-page.component';
import { StylesPageComponent } from './pages/styles-page/styles-page.component';

export const REFERENCE_ROUTES: Routes = [
  { path: '', component: ReferencePageComponent },
  { path: 'styles', component: StylesPageComponent },
];
