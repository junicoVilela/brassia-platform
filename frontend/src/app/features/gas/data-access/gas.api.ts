import { HttpClient, HttpParams } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { map } from 'rxjs';
import {
  ApplyRevisionRequest,
  BalanceInput,
  ConnectGasRequest,
  GasComponent,
  GasConnection,
  GasConnectionDetail,
  GasCylinder,
  GasTubing,
  LineBalance,
  PressureResult,
  RegisterCylinderRequest,
  ServiceLine,
  ServiceLineDetail,
} from '../domain/gas.model';

export interface EquipmentOption {
  id: string;
  code: string;
  name: string;
}

/**
 * Envelope de paginação do backend. Endpoints de listagem devolvem
 * `{content, page, size, ...}` — não um array cru.
 */
interface PageResponse<T> {
  content: T[];
  page: number;
  size: number;
  totalElements: number;
  totalPages: number;
}

@Injectable({ providedIn: 'root' })
export class GasApi {
  private readonly http = inject(HttpClient);
  private readonly cylindersUrl = '/api/v1/gas/cylinders';
  private readonly componentsUrl = '/api/v1/gas/components';
  private readonly connectionsUrl = '/api/v1/gas/connections';
  private readonly serviceLinesUrl = '/api/v1/gas/service-lines';
  private readonly tubingUrl = '/api/v1/gas/tubing';

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
    return this.http.get<PageResponse<EquipmentOption>>('/api/v1/equipment', {
      params: { size: '200' },
    }).pipe(map(p => p.content));
  }

  // --- linha de serviço (GAS-002) ---

  serviceLines() {
    return this.http.get<ServiceLine[]>(this.serviceLinesUrl);
  }

  serviceLine(lineId: string) {
    return this.http.get<ServiceLineDetail>(`${this.serviceLinesUrl}/${lineId}`);
  }

  registerServiceLine(code: string, name: string, pointOfUseEquipmentId: string) {
    return this.http.post<{ id: string }>(this.serviceLinesUrl, { code, name, pointOfUseEquipmentId });
  }

  /** Calcula e explica; nada é aplicado nem ajustado na rede. */
  balance(lineId: string, input: BalanceInput) {
    const params = new HttpParams()
      .set('targetCo2Volumes', input.targetCo2Volumes)
      .set('servingTempC', input.servingTempC)
      .set('elevationMeters', input.elevationMeters)
      .set('residualPressureBar', input.residualPressureBar)
      .set('targetFlowLpm', input.targetFlowLpm)
      .set('resistanceId', input.resistanceId);
    return this.http.get<LineBalance>(`${this.serviceLinesUrl}/${lineId}/balance`, { params });
  }

  /** Aplicar gera revisão nova e preserva a anterior. */
  applyRevision(lineId: string, request: ApplyRevisionRequest) {
    return this.http.post<{ revision: number; recommendedLengthMeters: number;
      lengthDeviationMeters: number }>(`${this.serviceLinesUrl}/${lineId}/revisions`, request);
  }

  tubing() {
    return this.http.get<GasTubing[]>(this.tubingUrl);
  }

  registerTubing(material: string, internalDiameterMm: number, resistanceBarPerMeter: number,
      referenceFlowLpm: number) {
    return this.http.post<{ id: string }>(this.tubingUrl,
      { material, internalDiameterMm, resistanceBarPerMeter, referenceFlowLpm });
  }
}
