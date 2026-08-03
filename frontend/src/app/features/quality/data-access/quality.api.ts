import { HttpClient } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import {
  AddPointRequest,
  ControlPlan,
  CreatePlanRequest,
  Deviation,
  Measurement,
  MeasurementOutcome,
  NonConformity,
  OpenNcRequest,
  RecordMeasurementRequest,
} from '../domain/quality.model';

@Injectable({ providedIn: 'root' })
export class QualityApi {
  private readonly http = inject(HttpClient);
  private readonly plansUrl = '/api/v1/quality/control-plans';
  private readonly ncUrl = '/api/v1/quality/non-conformities';

  plans() {
    return this.http.get<ControlPlan[]>(this.plansUrl);
  }

  create(request: CreatePlanRequest) {
    return this.http.post<ControlPlan>(this.plansUrl, request);
  }

  addPoint(planId: string, request: AddPointRequest) {
    return this.http.post<ControlPlan>(`${this.plansUrl}/${planId}/points`, request);
  }

  removePoint(planId: string, pointId: string) {
    return this.http.delete<ControlPlan>(`${this.plansUrl}/${planId}/points/${pointId}`);
  }

  publish(planId: string) {
    return this.http.post<ControlPlan>(`${this.plansUrl}/${planId}/publish`, {});
  }

  newVersion(planId: string) {
    return this.http.post<ControlPlan>(`${this.plansUrl}/${planId}/new-version`, {});
  }

  measurements(planId: string) {
    return this.http.get<Measurement[]>(`${this.plansUrl}/${planId}/measurements`);
  }

  measure(request: RecordMeasurementRequest) {
    return this.http.post<MeasurementOutcome>('/api/v1/quality/measurements', request);
  }

  deviations() {
    return this.http.get<Deviation[]>('/api/v1/quality/deviations');
  }

  // --- não conformidade e CAPA (QLT-002) ---

  nonConformities() {
    return this.http.get<NonConformity[]>(this.ncUrl);
  }

  openNc(request: OpenNcRequest) {
    return this.http.post<NonConformity>(this.ncUrl, request);
  }

  contain(ncId: string, description: string) {
    return this.http.post<NonConformity>(`${this.ncUrl}/${ncId}/containment`, { description });
  }

  investigate(ncId: string, rootCause: string, method: string) {
    return this.http.post<NonConformity>(`${this.ncUrl}/${ncId}/investigation`, { rootCause, method });
  }

  planAction(ncId: string, request: { kind: string; description: string; owner: string; dueOn: string }) {
    return this.http.post<NonConformity>(`${this.ncUrl}/${ncId}/actions`, request);
  }

  completeAction(ncId: string, actionId: string) {
    return this.http.post<NonConformity>(`${this.ncUrl}/${ncId}/actions/${actionId}/complete`, {});
  }

  verify(ncId: string, effective: boolean, evidence: string) {
    return this.http.post<NonConformity>(`${this.ncUrl}/${ncId}/verification`, { effective, evidence });
  }

  closeNc(ncId: string) {
    return this.http.post<NonConformity>(`${this.ncUrl}/${ncId}/close`, {});
  }
}
