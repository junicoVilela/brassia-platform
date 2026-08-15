import { HttpClient, HttpParams } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';
import { PriceSchedule, Product, SalesChannel } from '../domain/product.model';

@Injectable({ providedIn: 'root' })
export class SalesApi {
  private readonly http = inject(HttpClient);
  private readonly baseUrl = '/api/v1/sales';

  products(onlyActive: boolean): Observable<Product[]> {
    return this.http.get<Product[]>(`${this.baseUrl}/products`, {
      params: new HttpParams().set('onlyActive', onlyActive),
    });
  }

  createProduct(body: {
    sku: string;
    name: string;
    recipeId: string;
    containerId: string;
  }): Observable<{ id: string }> {
    return this.http.post<{ id: string }>(`${this.baseUrl}/products`, body);
  }

  setProductActive(id: string, active: boolean): Observable<void> {
    return this.http.put<void>(`${this.baseUrl}/products/${id}/active`, { active });
  }

  channels(onlyActive: boolean): Observable<SalesChannel[]> {
    return this.http.get<SalesChannel[]>(`${this.baseUrl}/channels`, {
      params: new HttpParams().set('onlyActive', onlyActive),
    });
  }

  createChannel(body: { code: string; name: string }): Observable<{ id: string }> {
    return this.http.post<{ id: string }>(`${this.baseUrl}/channels`, body);
  }

  priceSchedule(productId: string, channelId: string): Observable<PriceSchedule> {
    return this.http.get<PriceSchedule>(`${this.baseUrl}/products/${productId}/prices`, {
      params: new HttpParams().set('channelId', channelId),
    });
  }

  /** `validFrom` é data pura (sem hora), então não há a armadilha de fuso do `datetime-local`. */
  priceFrom(
    productId: string,
    body: {
      channelId: string;
      amount: number;
      currency: string;
      taxIncluded: boolean;
      validFrom: string;
    },
  ): Observable<void> {
    return this.http.post<void>(`${this.baseUrl}/products/${productId}/prices`, body);
  }
}
