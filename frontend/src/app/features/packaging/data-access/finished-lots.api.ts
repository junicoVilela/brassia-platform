import { HttpClient } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';
import { FinishedLot, RecordShipmentRequest, Shipment } from '../domain/finished-lot.model';

@Injectable({ providedIn: 'root' })
export class FinishedLotsApi {
  private readonly http = inject(HttpClient);

  lots(): Observable<FinishedLot[]> {
    return this.http.get<FinishedLot[]>('/api/v1/packaging/finished-lots');
  }

  shipments(): Observable<Shipment[]> {
    return this.http.get<Shipment[]>('/api/v1/packaging/shipments');
  }

  ship(request: RecordShipmentRequest): Observable<Shipment> {
    return this.http.post<Shipment>('/api/v1/packaging/shipments', request);
  }
}
