import { HttpClient } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import {
  RecordMovementRequest,
  ReceiveStockLotRequest,
  StockBalance,
  StockLot,
  StockMovement,
} from '../domain/stock-lot.model';

@Injectable({ providedIn: 'root' })
export class InventoryApi {
  private readonly http = inject(HttpClient);
  private readonly baseUrl = '/api/v1/inventory/lots';

  list() {
    return this.http.get<StockLot[]>(this.baseUrl);
  }

  receive(request: ReceiveStockLotRequest) {
    return this.http.post<StockLot>(this.baseUrl, request);
  }

  balance(lotId: string) {
    return this.http.get<StockBalance>(`${this.baseUrl}/${lotId}/balance`);
  }

  movements(lotId: string) {
    return this.http.get<StockMovement[]>(`${this.baseUrl}/${lotId}/movements`);
  }

  recordMovement(lotId: string, request: RecordMovementRequest) {
    return this.http.post<StockBalance>(`${this.baseUrl}/${lotId}/movements`, request);
  }
}
