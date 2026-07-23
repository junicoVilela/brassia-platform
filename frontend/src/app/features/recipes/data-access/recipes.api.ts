import { HttpClient } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import {
  CalculatedMetrics,
  CreateRecipeRequest,
  CreatedRecipe,
  RecipeSummary,
  VolumeBalance,
} from '../domain/recipe.model';

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

  create(request: CreateRecipeRequest) {
    return this.http.post<CreatedRecipe>(this.baseUrl, request);
  }

  volumes(recipeId: string) {
    return this.http.get<VolumeBalance>(`${this.baseUrl}/${recipeId}/volumes`);
  }

  calculateMetrics(recipeId: string) {
    return this.http.post<CalculatedMetrics>(`${this.baseUrl}/${recipeId}/metrics`, {});
  }
}
