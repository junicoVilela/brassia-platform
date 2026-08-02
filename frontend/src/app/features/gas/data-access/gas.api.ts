import { HttpClient, HttpParams } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import {
  ConnectGasRequest,
  GasComponent,
  GasConnection,
  GasConnectionDetail,
  GasCylinder,
  PressureResult,
  RegisterCylinderRequest,
} from '../domain/gas.model';

export interface EquipmentOption {
  id: string;
  code: string;
  name: string;
}

@Injectable({ providedIn: 'root' })
export class GasApi {
  private readonly http = inject(HttpClient);
  private readonly cylindersUrl = '/api/v1/gas/cylinders';
  private readonly componentsUrl = '/api/v1/gas/components';
  private readonly connectionsUrl = '/api/v1/gas/connections';

  cylinders() {
    return this.http.get<GasCylinder[]>(this.cylindersUrl);
  }

  registerCylinder(request: RegisterCylinderRequest) {
    return this.http.post<{ id: string }>(this.cylindersUrl, request);
  }

  setCylinderBlock(cylinderId: string, blocked: boolean, reason: string | null) {
    return this.http.post<void>(`${this.cylindersUrl}/${cylinderId}/block`, { blocked, reason });
  }

  requalify(cylinderId: string, dueOn: string) {
    return this.http.post<void>(`${this.cylindersUrl}/${cylinderId}/requalification`, { dueOn });
  }

  refill(cylinderId: string, contentKg: number) {
    return this.http.post<void>(`${this.cylindersUrl}/${cylinderId}/refill`, { contentKg });
  }

  components() {
    return this.http.get<GasComponent[]>(this.componentsUrl);
  }

  connections(onlyOpen: boolean) {
    return this.http.get<GasConnection[]>(this.connectionsUrl, {
      params: new HttpParams().set('onlyOpen', onlyOpen),
    });
  }

  connection(connectionId: string) {
    return this.http.get<GasConnectionDetail>(`${this.connectionsUrl}/${connectionId}`);
  }

  connect(request: ConnectGasRequest) {
    return this.http.post<{ id: string }>(this.connectionsUrl, request);
  }

  leakTest(connectionId: string, passed: boolean, method: string, pressureDropBar: number, note: string | null) {
    return this.http.post<void>(`${this.connectionsUrl}/${connectionId}/leak-test`, {
      passed,
      method,
      pressureDropBar,
      note,
    });
  }

  pressure(connectionId: string, bar: number, tempC: number | null) {
    return this.http.post<PressureResult>(`${this.connectionsUrl}/${connectionId}/pressure`, { bar, tempC });
  }

  consumption(connectionId: string, kg: number, reason: string | null) {
    return this.http.post<void>(`${this.connectionsUrl}/${connectionId}/consumption`, { kg, reason });
  }

  disconnect(connectionId: string, reason: string) {
    return this.http.post<void>(`${this.connectionsUrl}/${connectionId}/disconnect`, { reason });
  }

  equipment() {
    return this.http.get<EquipmentOption[]>('/api/v1/equipment');
  }
}
