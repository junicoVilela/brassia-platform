import { HttpClient } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { PurchaseNeed } from '../domain/purchase-need.model';

@Injectable({ providedIn: 'root' })
export class PurchaseNeedsApi {
  private readonly http = inject(HttpClient);
  private readonly baseUrl = '/api/v1/purchasing/needs';

  list() {
    return this.http.get<PurchaseNeed[]>(this.baseUrl);
  }
}
