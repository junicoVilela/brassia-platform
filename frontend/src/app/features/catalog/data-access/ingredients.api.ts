import { HttpClient } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Ingredient, IngredientType, RegisterIngredientRequest } from '../domain/ingredient.model';

interface PageResponse<T> {
  content: T[];
  page: number;
  size: number;
  totalElements: number;
  totalPages: number;
}

@Injectable({ providedIn: 'root' })
export class IngredientsApi {
  private readonly http = inject(HttpClient);
  private readonly baseUrl = '/api/v1/catalog/ingredients';

  list(type?: IngredientType) {
    const params: Record<string, string> = {};
    if (type) {
      params['type'] = type;
    }
    return this.http.get<PageResponse<Ingredient>>(this.baseUrl, { params });
  }

  create(request: RegisterIngredientRequest) {
    return this.http.post<Ingredient>(this.baseUrl, request);
  }
}
