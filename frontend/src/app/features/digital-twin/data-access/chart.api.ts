import { HttpClient } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';
import { AnalyzeChartRequest, ControlChart } from '../domain/chart.model';

@Injectable({ providedIn: 'root' })
export class ChartApi {
  private readonly http = inject(HttpClient);
  private readonly baseUrl = '/api/v1/digital-twin/control-charts';

  /** POST, e não GET: a amostra é uma lista de lotes que estoura o limite de URL num ano de histórico. */
  analyze(request: AnalyzeChartRequest): Observable<ControlChart> {
    return this.http.post<ControlChart>(this.baseUrl, request);
  }
}
