import { HttpClient } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import {
  CreateScheduleEntryRequest,
  CreatedScheduleEntry,
  MaterialLine,
  ScheduleEntry,
  SimulateScheduleRequest,
  SimulateScheduleResult,
} from '../domain/schedule.model';

@Injectable({ providedIn: 'root' })
export class PlanningApi {
  private readonly http = inject(HttpClient);
  private readonly baseUrl = '/api/v1/planning/schedule';

  list(from: string, to: string) {
    return this.http.get<ScheduleEntry[]>(this.baseUrl, { params: { from, to } });
  }

  create(request: CreateScheduleEntryRequest) {
    return this.http.post<CreatedScheduleEntry>(this.baseUrl, request);
  }

  simulate(request: SimulateScheduleRequest) {
    return this.http.post<SimulateScheduleResult>(`${this.baseUrl}/simulate`, request);
  }

  materials(entryId: string) {
    return this.http.get<MaterialLine[]>(`${this.baseUrl}/${entryId}/materials`);
  }
}
