import { HttpClient } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';
import { Budget, GatewayStatus, ProbeResult } from '../domain/gateway.model';

@Injectable({ providedIn: 'root' })
export class AiApi {
  private readonly http = inject(HttpClient);
  private readonly baseUrl = '/api/v1/ai/gateway';

  status(): Observable<GatewayStatus> {
    return this.http.get<GatewayStatus>(this.baseUrl);
  }

  /** POST porque gasta: uma verificação de conectividade não é consulta. */
  probe(): Observable<ProbeResult> {
    return this.http.post<ProbeResult>(`${this.baseUrl}/probe`, {});
  }

  redefineBudget(monthlyLimit: number, version: number): Observable<Budget> {
    return this.http.put<Budget>(`${this.baseUrl}/budget`, { monthlyLimit, version });
  }
}
