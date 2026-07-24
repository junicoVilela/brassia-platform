import { HttpClient } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import {
  CreateTechnicalProfileRequest,
  Ingredient,
  IngredientType,
  RegisterIngredientRequest,
  TechnicalProfile,
} from '../domain/ingredient.model';

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

  getProfile(ingredientId: string) {
    return this.http.get<TechnicalProfile>(`${this.baseUrl}/${ingredientId}/technical-profile`);
  }

  createProfile(ingredientId: string, request: CreateTechnicalProfileRequest) {
    return this.http.post<{ id: string; status: string }>(
      `${this.baseUrl}/${ingredientId}/technical-profile`,
      request,
    );
  }

  publishProfile(ingredientId: string) {
    return this.http.post<{ status: string }>(`${this.baseUrl}/${ingredientId}/technical-profile/publish`, {});
  }
}
