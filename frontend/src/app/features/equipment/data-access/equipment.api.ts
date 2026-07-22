import { HttpClient } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Equipment, RegisterEquipmentRequest } from '../domain/equipment.model';

interface PageResponse<T> {
  content: T[];
  page: number;
  size: number;
  totalElements: number;
  totalPages: number;
}

@Injectable({ providedIn: 'root' })
export class EquipmentApi {
  private readonly http = inject(HttpClient);
  private readonly baseUrl = '/api/v1/equipment';

  list(page = 0, size = 20) {
    return this.http.get<PageResponse<Equipment>>(this.baseUrl, { params: { page, size } });
  }

  create(request: RegisterEquipmentRequest) {
    return this.http.post<Equipment>(this.baseUrl, request);
  }
}
