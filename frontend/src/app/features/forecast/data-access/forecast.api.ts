import { HttpClient } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';
import { DemandForecast } from '../domain/forecast.model';

@Injectable({ providedIn: 'root' })
export class ForecastApi {
  private readonly http = inject(HttpClient);

  demand(productId: string): Observable<DemandForecast> {
    return this.http.get<DemandForecast>(`/api/v1/forecast/products/${productId}/demand`);
  }
}
