import { Routes } from '@angular/router';

export const RECIPE_ROUTES: Routes = [
  {
    path: '',
    loadComponent: () =>
      import('./pages/recipe-list-page/recipe-list-page.component')
        .then(m => m.RecipeListPageComponent),
  },
];
