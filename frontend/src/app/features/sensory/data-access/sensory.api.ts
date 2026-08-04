import { HttpClient } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import {
  AddSampleRequest,
  CreateSessionRequest,
  SensorySession,
  SessionResults,
  SubmitEvaluationRequest,
} from '../domain/sensory.model';

@Injectable({ providedIn: 'root' })
export class SensoryApi {
  private readonly http = inject(HttpClient);
  private readonly baseUrl = '/api/v1/sensory/sessions';

  sessions() {
    return this.http.get<SensorySession[]>(this.baseUrl);
  }

  create(request: CreateSessionRequest) {
    return this.http.post<SensorySession>(this.baseUrl, request);
  }

  addSample(sessionId: string, request: AddSampleRequest) {
    return this.http.post<SensorySession>(`${this.baseUrl}/${sessionId}/samples`, request);
  }

  removeSample(sessionId: string, sampleId: string) {
    return this.http.delete<SensorySession>(`${this.baseUrl}/${sessionId}/samples/${sampleId}`);
  }

  open(sessionId: string) {
    return this.http.post<SensorySession>(`${this.baseUrl}/${sessionId}/open`, {});
  }

  close(sessionId: string) {
    return this.http.post<SensorySession>(`${this.baseUrl}/${sessionId}/close`, {});
  }

  submit(sessionId: string, request: SubmitEvaluationRequest) {
    return this.http.post<SensorySession>(`${this.baseUrl}/${sessionId}/evaluations`, request);
  }

  /** Recusado com 409 enquanto a sessão não é encerrada. */
  results(sessionId: string) {
    return this.http.get<SessionResults>(`${this.baseUrl}/${sessionId}/results`);
  }
}
