import { HttpClient } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { CalculationResult, CalculatorSpec } from '../domain/calculator.model';

@Injectable({ providedIn: 'root' })
export class CalculatorsApi {
  private readonly http = inject(HttpClient);
  private readonly baseUrl = '/api/v1/calculators';

  list() {
    return this.http.get<CalculatorSpec[]>(this.baseUrl);
  }

  compute(id: string, inputs: Record<string, number>) {
    return this.http.post<CalculationResult>(`${this.baseUrl}/${id}`, { inputs });
  }
}
