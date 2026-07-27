import { HttpClient } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { CompatibilityRule, CreateRuleRequest, RecommendRequest } from '../domain/matrix.model';

@Injectable({ providedIn: 'root' })
export class MatrixApi {
  private readonly http = inject(HttpClient);
  private readonly baseUrl = '/api/v1/sanitation/matrix';

  list() {
    return this.http.get<CompatibilityRule[]>(this.baseUrl);
  }

  create(request: CreateRuleRequest) {
    return this.http.post<{ id: string }>(this.baseUrl, request);
  }

  recommend(request: RecommendRequest) {
    return this.http.post<CompatibilityRule>(`${this.baseUrl}/recommend`, request);
  }
}
