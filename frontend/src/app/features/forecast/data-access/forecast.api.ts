import { HttpClient } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';
import { DemandForecast, CapacityView } from '../domain/forecast.model';

@Injectable({ providedIn: 'root' })
export class ForecastApi {
  private readonly http = inject(HttpClient);

  private readonly baseUrl = '/api/v1/forecast';

  demand(productId: string): Observable<DemandForecast> {
    return this.http.get<DemandForecast>(`${this.baseUrl}/products/${productId}/demand`);
  }

  /** A capacidade do próximo mês, e se a demanda cabe. */
  capacity(productId: string): Observable<CapacityView> {
    return this.http.get<CapacityView>(`${this.baseUrl}/products/${productId}/capacity`);
  }

  declareTankCycle(equipmentId: string, cycleDays: number, note: string | null): Observable<void> {
    return this.http.put<void>(`${this.baseUrl}/tank-cycles/${equipmentId}`, { cycleDays, note });
  }

  removeTankCycle(equipmentId: string): Observable<void> {
    return this.http.delete<void>(`${this.baseUrl}/tank-cycles/${equipmentId}`);
  }
}
