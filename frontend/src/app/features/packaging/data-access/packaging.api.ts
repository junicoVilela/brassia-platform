import { HttpClient, HttpParams } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { MAX_PAGE_SIZE, PageResponse } from '../../../core/http/page.model';
import { map } from 'rxjs';
import {
  Carbonation,
  CarbonationInput,
  CarbonationRecommendation,
  ChecklistItemCode,
  ExecutePackagingRequest,
  Freshness,
  LabelFieldCode,
  LabelPreview,
  LabelPrint,
  LabelTemplate,
  PackagingPlan,
  PackagingRun,
  PlanPackagingRequest,
  RecordFreshnessRequest,
  RecordedFreshness,
  ReserveResult,
  ShelfLifePolicy,
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

/**
 * Envelope de paginação do backend. Endpoints de listagem devolvem
 * `{content, page, size, ...}` — não um array cru.
 */

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
    // A listagem é paginada (REL-002). Estes clientes só preenchem seletor: pedem o teto de uma
    // página e mapeiam `content`. O que NÃO se faz aqui é ignorar o truncamento — quem consome
    // decide o que mostrar, mas o dado de que há mais vem junto.
    return this.http
      .get<PageResponse<BatchOption>>('/api/v1/production/batches', {
        params: new HttpParams().set('page', 0).set('size', MAX_PAGE_SIZE),
      })
      .pipe(map(page => page.content));
  }

  ingredients() {
    return this.http.get<PageResponse<IngredientOption>>('/api/v1/catalog/ingredients', {
      params: { size: '200' },
    }).pipe(map(p => p.content));
  }

  equipment() {
    return this.http.get<PageResponse<EquipmentOption>>('/api/v1/equipment', {
      params: { size: '200' },
    }).pipe(map(p => p.content));
  }

  /** Registra DO/TPO, purga e vedação, e devolve a validade recomendada (FSL-001). */
  recordFreshness(planId: string, request: RecordFreshnessRequest) {
    return this.http.put<RecordedFreshness>(`${this.baseUrl}/${planId}/freshness`, request);
  }

  freshness(planId: string) {
    return this.http.get<Freshness>(`${this.baseUrl}/${planId}/freshness`);
  }

  /** O motivo é obrigatório: é ele que explica uma data que a evidência não sustentava. */
  overrideShelfLife(planId: string, shelfLifeDays: number, reason: string) {
    return this.http.post<void>(`${this.baseUrl}/${planId}/freshness/override`, { shelfLifeDays, reason });
  }

  shelfLifePolicy() {
    return this.http.get<ShelfLifePolicy>('/api/v1/packaging/shelf-life-policy');
  }

  // --- rótulo (PKG-004) ---

  labelTemplates() {
    return this.http.get<LabelTemplate[]>('/api/v1/packaging/label-templates');
  }

  labelRule() {
    return this.http.get<{ requiredFields: LabelFieldCode[] }>('/api/v1/packaging/label-rule');
  }

  /** Prévia: valores, origens e o que falta — sem imprimir nada. */
  labelPreview(planId: string, templateId: string) {
    return this.http.get<LabelPreview>(`${this.baseUrl}/${planId}/label/preview`,
      { params: new HttpParams().set('templateId', templateId) });
  }

  labelPrints(planId: string) {
    return this.http.get<LabelPrint[]>(`${this.baseUrl}/${planId}/label/prints`);
  }

  /** A partir da segunda tiragem o backend exige o motivo. */
  printLabel(planId: string, templateId: string, quantity: number, reason: string | null) {
    return this.http.post<{ printId: string; reprint: boolean; quantity: number }>(
      `${this.baseUrl}/${planId}/label/prints`, { templateId, quantity, reason });
  }

  /** Registra o envase executado; a perda é derivada pelo backend (PKG-003). */
  execute(planId: string, request: ExecutePackagingRequest) {
    return this.http.post<{
      runId: string;
      packagedVolumeLiters: number;
      lossesLiters: number;
      containersConsumed: number;
    }>(`${this.baseUrl}/${planId}/execution`, request);
  }

  run(planId: string) {
    return this.http.get<PackagingRun>(`${this.baseUrl}/${planId}/execution`);
  }

  /** Prévia de carbonatação: calcula e explica sem gravar nada (PKG-002). */
  previewCarbonation(planId: string, input: CarbonationInput) {
    let params = new HttpParams()
      .set('method', input.method)
      .set('targetVolumes', input.targetVolumes)
      .set('referenceTempC', input.referenceTempC);
    if (input.primingSugar) {
      params = params.set('primingSugar', input.primingSugar);
    }
    return this.http.get<CarbonationRecommendation>(`${this.baseUrl}/${planId}/carbonation/preview`, { params });
  }

  carbonation(planId: string) {
    return this.http.get<Carbonation>(`${this.baseUrl}/${planId}/carbonation`);
  }

  /** A confirmação nunca é implícita: o backend recusa `confirmed: false`. */
  recordCarbonation(planId: string, input: CarbonationInput) {
    return this.http.put<void>(`${this.baseUrl}/${planId}/carbonation`, { ...input, confirmed: true });
  }
}
