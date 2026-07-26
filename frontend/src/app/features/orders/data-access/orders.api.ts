import { HttpClient } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import {
  BrewOrderDetail,
  BrewOrderSummary,
  CreateBrewOrderRequest,
  CreatedBrewOrder,
} from '../domain/order.model';

interface PageResponse<T> {
  content: T[];
  page: number;
  size: number;
  totalElements: number;
  totalPages: number;
}

@Injectable({ providedIn: 'root' })
export class OrdersApi {
  private readonly http = inject(HttpClient);
  private readonly baseUrl = '/api/v1/brew-orders';

  list() {
    return this.http.get<PageResponse<BrewOrderSummary>>(this.baseUrl);
  }

  create(request: CreateBrewOrderRequest) {
    return this.http.post<CreatedBrewOrder>(this.baseUrl, request);
  }

  get(orderId: string) {
    return this.http.get<BrewOrderDetail>(`${this.baseUrl}/${orderId}`);
  }

  release(orderId: string, assignedUserId: string) {
    return this.http.post<{ id: string; status: string }>(`${this.baseUrl}/${orderId}/release`, { assignedUserId });
  }

  cancel(orderId: string, reason: string) {
    return this.http.post<{ id: string; status: string }>(`${this.baseUrl}/${orderId}/cancel`, { reason });
  }
}
