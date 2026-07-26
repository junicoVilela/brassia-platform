import { HttpClient } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { CreateCountRequest, PhysicalCount } from '../domain/physical-count.model';

@Injectable({ providedIn: 'root' })
export class CountsApi {
  private readonly http = inject(HttpClient);
  private readonly baseUrl = '/api/v1/inventory/counts';

  list() {
    return this.http.get<PhysicalCount[]>(this.baseUrl);
  }

  create(request: CreateCountRequest) {
    return this.http.post<{ id: string; status: string }>(this.baseUrl, request);
  }

  approve(countId: string) {
    return this.http.post<{ id: string; status: string; adjustments: number }>(
      `${this.baseUrl}/${countId}/approve`, {});
  }
}
