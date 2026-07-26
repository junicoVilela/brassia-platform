import { HttpClient } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { RegisterSupplierRequest, Supplier } from '../domain/supplier.model';

@Injectable({ providedIn: 'root' })
export class SuppliersApi {
  private readonly http = inject(HttpClient);
  private readonly baseUrl = '/api/v1/suppliers';

  list() {
    return this.http.get<Supplier[]>(this.baseUrl);
  }

  create(request: RegisterSupplierRequest) {
    return this.http.post<Supplier>(this.baseUrl, request);
  }
}
