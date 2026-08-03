import { HttpClient } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import {
  AddPointRequest,
  ControlPlan,
  CreatePlanRequest,
  Deviation,
  Measurement,
  MeasurementOutcome,
  RecordMeasurementRequest,
} from '../domain/quality.model';

@Injectable({ providedIn: 'root' })
export class QualityApi {
  private readonly http = inject(HttpClient);
  private readonly plansUrl = '/api/v1/quality/control-plans';

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
}
