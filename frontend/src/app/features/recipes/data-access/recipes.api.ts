import { HttpClient } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { RecipeSummary } from '../domain/recipe.model';

interface PageResponse<T> {
  content: T[];
  page: number;
  size: number;
  totalElements: number;
  totalPages: number;
}

@Injectable({ providedIn: 'root' })
export class RecipesApi {
  private readonly http = inject(HttpClient);
  private readonly baseUrl = '/api/v1/recipes';

  list() {
    return this.http.get<PageResponse<RecipeSummary>>(this.baseUrl);
  }
}
