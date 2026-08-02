import { HttpClient, HttpParams } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import {
  ChecklistItemCode,
  PackagingPlan,
  PlanPackagingRequest,
  ReserveResult,
} from '../domain/packaging-plan.model';

/** Opção de lote para o formulário; o backend publica mais campos, usamos só estes. */
export interface BatchOption {
  id: string;
  code: string;
  recipeName: string;
  status: string;
}

export interface IngredientOption {
  id: string;
  code: string;
  name: string;
  type: string;
}

export interface EquipmentOption {
  id: string;
  code: string;
  name: string;
}

@Injectable({ providedIn: 'root' })
export class PackagingApi {
  private readonly http = inject(HttpClient);
  private readonly baseUrl = '/api/v1/packaging/plans';

  list(batchId: string | null) {
    let params = new HttpParams();
    if (batchId) {
      params = params.set('batchId', batchId);
    }
    return this.http.get<PackagingPlan[]>(this.baseUrl, { params });
  }

  get(planId: string) {
    return this.http.get<PackagingPlan>(`${this.baseUrl}/${planId}`);
  }

  plan(request: PlanPackagingRequest) {
    return this.http.post<{ id: string; plannedVolumeLiters: number }>(this.baseUrl, request);
  }

  confirm(planId: string, item: ChecklistItemCode) {
    return this.http.post<void>(`${this.baseUrl}/${planId}/checklist`, { item });
  }

  reserve(planId: string) {
    return this.http.post<ReserveResult>(`${this.baseUrl}/${planId}/reserve`, {});
  }

  cancel(planId: string, reason: string) {
    return this.http.post<void>(`${this.baseUrl}/${planId}/cancel`, { reason });
  }

  batches() {
    return this.http.get<BatchOption[]>('/api/v1/production/batches');
  }

  ingredients() {
    return this.http.get<IngredientOption[]>('/api/v1/catalog/ingredients');
  }

  equipment() {
    return this.http.get<EquipmentOption[]>('/api/v1/equipment');
  }
}
