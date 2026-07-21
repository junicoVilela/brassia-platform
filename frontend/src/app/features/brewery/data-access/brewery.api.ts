import { HttpClient } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { BrewerySummary, RegisterBreweryRequest } from '../domain/brewery.model';

interface PageResponse<T> {
  content: T[];
  page: number;
  size: number;
  totalElements: number;
  totalPages: number;
}

@Injectable({ providedIn: 'root' })
export class BreweryApi {
  private readonly http = inject(HttpClient);
  private readonly baseUrl = '/api/v1/breweries';

  list(page = 0, size = 20) {
    return this.http.get<PageResponse<BrewerySummary>>(this.baseUrl, { params: { page, size } });
  }

  register(request: RegisterBreweryRequest) {
    return this.http.post<BrewerySummary>(this.baseUrl, request);
  }
}
