import { HttpClient } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { map } from 'rxjs';
import {
  AccessReview,
  CreateReview,
  CreateRule,
  NamedRef,
  ReviewDecision,
  ReviewItem,
  SegregationRule,
} from '../domain/access-review.model';

interface PageResponse<T> {
  content: T[];
}

@Injectable({ providedIn: 'root' })
export class AccessReviewApi {
  private readonly http = inject(HttpClient);
  private readonly baseUrl = '/api/v1/security';

  listReviews() {
    return this.http.get<AccessReview[]>(`${this.baseUrl}/access-reviews`);
  }

  createReview(body: CreateReview) {
    return this.http.post<{ id: string }>(`${this.baseUrl}/access-reviews`, body);
  }

  listItems(reviewId: string) {
    return this.http.get<ReviewItem[]>(`${this.baseUrl}/access-reviews/${reviewId}/items`);
  }

  decideItem(itemId: string, decision: ReviewDecision, justification: string) {
    return this.http.post<void>(`${this.baseUrl}/access-reviews/items/${itemId}/decide`, { decision, justification });
  }

  listRules() {
    return this.http.get<SegregationRule[]>(`${this.baseUrl}/segregation-rules`);
  }

  createRule(body: CreateRule) {
    return this.http.post<{ id: string }>(`${this.baseUrl}/segregation-rules`, body);
  }

  // --- Catálogos auxiliares (opcionais: podem falhar por permissão) ---

  listUsers() {
    return this.http
      .get<PageResponse<{ id: string; displayName: string }>>(`${this.baseUrl}/users`, {
        params: { page: 0, size: 200 },
      })
      .pipe(map(page => page.content.map((u): NamedRef => ({ id: u.id, name: u.displayName }))));
  }

  listGroups() {
    return this.http
      .get<{ id: string; name: string }[]>(`${this.baseUrl}/groups`)
      .pipe(map(groups => groups.map((g): NamedRef => ({ id: g.id, name: g.name }))));
  }

  listPermissions() {
    return this.http
      .get<{ code: string }[]>(`${this.baseUrl}/permissions`)
      .pipe(map(permissions => permissions.map(p => p.code)));
  }
}
