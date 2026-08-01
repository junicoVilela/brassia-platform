import { HttpClient } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import {
  AddStepRequest,
  FermentationSchedule,
  PlanScheduleRequest,
  ReschedulePreview,
} from '../domain/schedule.model';

@Injectable({ providedIn: 'root' })
export class ScheduleApi {
  private readonly http = inject(HttpClient);

  private base(batchId: string) {
    return `/api/v1/fermentation/batches/${batchId}/schedule`;
  }

  get(batchId: string) {
    return this.http.get<FermentationSchedule>(this.base(batchId));
  }

  plan(batchId: string, request: PlanScheduleRequest) {
    return this.http.post<{ id: string; steps: number }>(this.base(batchId), request);
  }

  addStep(batchId: string, request: AddStepRequest) {
    return this.http.post<{ id: string }>(`${this.base(batchId)}/steps`, request);
  }

  /** apply=false devolve só a prévia; nada é gravado. */
  reschedule(batchId: string, stepId: string, newStart: string, apply: boolean) {
    return this.http.post<ReschedulePreview>(
      `${this.base(batchId)}/steps/${stepId}/reschedule`, { newStart, apply });
  }

  execute(batchId: string, stepId: string, executedAt: string, justification: string | null) {
    return this.http.post<void>(
      `${this.base(batchId)}/steps/${stepId}/execute`, { executedAt, justification });
  }
}
