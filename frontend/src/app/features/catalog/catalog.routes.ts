import { Routes } from '@angular/router';
import { IngredientsPageComponent } from './pages/ingredients-page/ingredients-page.component';

export const CATALOG_ROUTES: Routes = [
  { path: 'ingredients', component: IngredientsPageComponent },
  { path: '', pathMatch: 'full', redirectTo: 'ingredients' },
];
