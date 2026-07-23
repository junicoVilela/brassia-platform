import { HttpClient } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Maintenance, ScheduleMaintenanceRequest } from '../domain/maintenance.model';

@Injectable({ providedIn: 'root' })
export class MaintenanceApi {
  private readonly http = inject(HttpClient);
  private base(equipmentId: string) {
    return `/api/v1/equipment/${equipmentId}/maintenance`;
  }

  list(equipmentId: string) {
    return this.http.get<Maintenance[]>(this.base(equipmentId));
  }

  schedule(equipmentId: string, request: ScheduleMaintenanceRequest) {
    return this.http.post<Maintenance>(this.base(equipmentId), request);
  }

  cancel(equipmentId: string, maintenanceId: string) {
    return this.http.post<void>(`${this.base(equipmentId)}/${maintenanceId}/cancel`, {});
  }
}
