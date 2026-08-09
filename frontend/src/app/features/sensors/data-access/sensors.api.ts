import { HttpClient } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';
import { DeviceStatus, RegisterDeviceRequest, SensorDevice, SensorReading } from '../domain/sensor.model';

@Injectable({ providedIn: 'root' })
export class SensorsApi {
  private readonly http = inject(HttpClient);
  private readonly baseUrl = '/api/v1/sensors';

  devices(): Observable<SensorDevice[]> {
    return this.http.get<SensorDevice[]>(`${this.baseUrl}/devices`);
  }

  register(request: RegisterDeviceRequest): Observable<SensorDevice> {
    return this.http.post<SensorDevice>(`${this.baseUrl}/devices`, request);
  }

  /**
   * `expectedVersion` não é detalhe de implementação que vazou: é o que impede dois operadores de
   * decidirem o destino do mesmo dispositivo sem que nenhum dos dois perceba.
   */
  changeStatus(deviceId: string, status: DeviceStatus, expectedVersion: number): Observable<SensorDevice> {
    return this.http.post<SensorDevice>(`${this.baseUrl}/devices/${deviceId}/status`, {
      status,
      expectedVersion,
    });
  }

  readings(deviceId: string, from: string, to: string): Observable<SensorReading[]> {
    return this.http.get<SensorReading[]>(`${this.baseUrl}/devices/${deviceId}/readings`, {
      params: { from, to },
    });
  }
}
