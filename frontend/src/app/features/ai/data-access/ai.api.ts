import { HttpClient } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';
import { AskRequest, GroundedAnswer } from '../domain/answer.model';
import { Assessment } from '../domain/assessment.model';
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

  /** POST porque gasta: cada pergunta é uma chamada ao modelo, cobrada e registrada. */
  ask(request: AskRequest): Observable<GroundedAnswer> {
    return this.http.post<GroundedAnswer>('/api/v1/ai/copilot/ask', request);
  }

  /** POST porque gasta, e alçada própria: avaliar um lote lê custo, qualidade e produção dele. */
  assess(batchId: string): Observable<Assessment> {
    return this.http.post<Assessment>(`/api/v1/ai/copilot/batches/${batchId}/assessment`, {});
  }
}
