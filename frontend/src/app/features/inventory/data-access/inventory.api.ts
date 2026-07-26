import { HttpClient } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { ReceiveStockLotRequest, StockLot } from '../domain/stock-lot.model';

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
}
