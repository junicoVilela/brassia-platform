import { Routes } from '@angular/router';

export const routes: Routes = [
  {
    path: 'recipes',
    loadChildren: () => import('./features/recipes/recipes.routes').then(m => m.RECIPE_ROUTES),
  },
  { path: '', pathMatch: 'full', redirectTo: 'recipes' },
];
