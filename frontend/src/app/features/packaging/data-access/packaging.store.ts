import { DestroyRef, Injectable, computed, inject, signal } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { finalize } from 'rxjs';
import { ToastService } from '../../../core/notifications/toast.service';
import {
  Carbonation,
  CarbonationInput,
  CarbonationRecommendation,
  ChecklistItemCode,
  OverCarbonation,
  PackagingBlocker,
  PackagingPlan,
  PackagingShortfall,
  PlanPackagingRequest,
} from '../domain/packaging-plan.model';
import { BatchOption, EquipmentOption, IngredientOption, PackagingApi } from './packaging.api';

/** Corpo Problem Details da recusa de reserva, como o backend o publica. */
interface ReserveError {
  status?: number;
  error?: { code?: string; blockers?: PackagingBlocker[]; shortfall?: PackagingShortfall };
}

/** Corpo Problem Details da recusa de carbonatação. */
interface CarbonationError {
  status?: number;
  error?: { code?: string; carbonation?: OverCarbonation };
}

/** Estado dos planos de envase (PKG-001). */
@Injectable()
export class PackagingStore {
  private readonly api = inject(PackagingApi);
  private readonly toast = inject(ToastService);
  private readonly destroyRef = inject(DestroyRef);

  private readonly itemsState = signal<PackagingPlan[]>([]);
  readonly items = this.itemsState.asReadonly();
  readonly batches = signal<BatchOption[]>([]);
  readonly containers = signal<IngredientOption[]>([]);
  readonly lines = signal<EquipmentOption[]>([]);

  readonly batchFilter = signal<string>('');
  readonly loading = signal(false);
  readonly error = signal<string | null>(null);
  readonly submitting = signal(false);
  readonly actionError = signal<string | null>(null);

  readonly empty = computed(() => !this.loading() && !this.error() && this.items().length === 0);

  /** Só lote em fermentação pode ser envasado — o backend recusa os demais. */
  readonly packageableBatches = computed(() => this.batches().filter(b => b.status === 'FERMENTING'));

  /**
   * Bloqueios da última tentativa de reserva, por plano. A tela mostra todos de uma vez em vez
   * de o operador descobrir um impedimento por tentativa.
   */
  readonly blockers = signal<Record<string, PackagingBlocker[]>>({});
  readonly shortfall = signal<Record<string, PackagingShortfall>>({});

  /** Carbonatação (PKG-002): prévia e decisão vivem separadas, como no backend. */
  readonly carbonationPlanId = signal<string | null>(null);
  readonly recommendation = signal<CarbonationRecommendation | null>(null);
  readonly carbonation = signal<Carbonation | null>(null);
  readonly overCarbonation = signal<OverCarbonation | null>(null);
  readonly calculating = signal(false);
  readonly carbonationError = signal<string | null>(null);

  load(): void {
    this.loading.set(true);
    this.error.set(null);
    this.api.list(this.batchFilter() || null)
      .pipe(finalize(() => this.loading.set(false)), takeUntilDestroyed(this.destroyRef))
      .subscribe({
        next: items => this.itemsState.set(items),
        error: () => this.error.set('Não foi possível carregar os planos de envase.'),
      });
  }

  loadReferences(): void {
    this.api.batches()
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe({ next: b => this.batches.set(b), error: () => this.batches.set([]) });
    this.api.ingredients()
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe({
        next: all => this.containers.set(all.filter(i => i.type === 'PACKAGING')),
        error: () => this.containers.set([]),
      });
    this.api.equipment()
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe({ next: e => this.lines.set(e), error: () => this.lines.set([]) });
  }

  filterByBatch(batchId: string): void {
    this.batchFilter.set(batchId);
    this.load();
  }

  plan(request: PlanPackagingRequest, onSuccess?: () => void): void {
    this.submitting.set(true);
    this.actionError.set(null);
    this.api.plan(request)
      .pipe(finalize(() => this.submitting.set(false)), takeUntilDestroyed(this.destroyRef))
      .subscribe({
        next: result => {
          onSuccess?.();
          this.toast.success(`Plano aberto: ${result.plannedVolumeLiters} L planejados.`);
          this.load();
        },
        error: (err: { status?: number }) =>
          this.actionError.set(err?.status === 409
            ? 'Código já usado ou o lote não está em fermentação.'
            : 'Não foi possível abrir o plano (volume acima do lote ou embalagem inválida).'),
      });
  }

  confirm(planId: string, item: ChecklistItemCode): void {
    this.api.confirm(planId, item)
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe({
        next: () => this.load(),
        error: (err: { status?: number }) =>
          this.toast.error(err?.status === 409
            ? 'O checklist só aceita confirmação enquanto o plano está planejado.'
            : 'Não foi possível confirmar o item.'),
      });
  }

  reserve(planId: string): void {
    this.clearRefusal(planId);
    this.api.reserve(planId)
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe({
        next: result => {
          this.toast.success(`Embalagem reservada: ${result.reservedUnits} ${result.unit}.`);
          this.load();
        },
        error: (err: ReserveError) => this.showRefusal(planId, err),
      });
  }

  cancel(planId: string, reason: string): void {
    this.api.cancel(planId, reason)
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe({
        next: () => {
          this.clearRefusal(planId);
          this.toast.success('Plano cancelado; a embalagem voltou ao estoque.');
          this.load();
        },
        error: (err: { status?: number }) =>
          this.toast.error(err?.status === 409
            ? 'Plano já cancelado; o cancelamento é definitivo.'
            : 'Não foi possível cancelar o plano.'),
      });
  }

  /**
   * Carbonatação do plano aberto (PKG-002). A prévia é recomendação e fica separada da decisão:
   * só o comando explícito de confirmar grava.
   */
  openCarbonationOf(planId: string): void {
    if (this.carbonationPlanId() === planId) {
      this.closeCarbonation();
      return;
    }
    this.carbonationPlanId.set(planId);
    this.recommendation.set(null);
    this.overCarbonation.set(null);
    this.loadCarbonation(planId);
  }

  closeCarbonation(): void {
    this.carbonationPlanId.set(null);
    this.recommendation.set(null);
    this.carbonation.set(null);
    this.overCarbonation.set(null);
  }

  preview(planId: string, input: CarbonationInput): void {
    this.calculating.set(true);
    this.overCarbonation.set(null);
    this.carbonationError.set(null);
    this.api.previewCarbonation(planId, input)
      .pipe(finalize(() => this.calculating.set(false)), takeUntilDestroyed(this.destroyRef))
      .subscribe({
        next: recommendation => this.recommendation.set(recommendation),
        error: () => this.carbonationError.set(
          'Não foi possível calcular (verifique método, alvo, temperatura e açúcar).'),
      });
  }

  /** Confirmar é ato explícito do cervejeiro; a prévia nunca grava sozinha. */
  confirmCarbonation(planId: string, input: CarbonationInput): void {
    this.calculating.set(true);
    this.overCarbonation.set(null);
    this.carbonationError.set(null);
    this.api.recordCarbonation(planId, input)
      .pipe(finalize(() => this.calculating.set(false)), takeUntilDestroyed(this.destroyRef))
      .subscribe({
        next: () => {
          this.toast.success('Carbonatação confirmada e registrada no plano.');
          this.loadCarbonation(planId);
        },
        error: (err: CarbonationError) => {
          if (err?.error?.code === 'over_carbonation' && err.error.carbonation) {
            this.overCarbonation.set(err.error.carbonation);
            return;
          }
          this.carbonationError.set(err?.status === 409
            ? 'O plano foi cancelado e não aceita carbonatação.'
            : 'Não foi possível confirmar a carbonatação.');
        },
      });
  }

  private loadCarbonation(planId: string): void {
    this.api.carbonation(planId)
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe({
        // 400 aqui significa "ainda não há decisão", não erro de operação.
        next: carbonation => this.carbonation.set(carbonation),
        error: () => this.carbonation.set(null),
      });
  }

  /** A recusa é informação acionável: guardamos os motivos ao lado do plano recusado. */
  private showRefusal(planId: string, err: ReserveError): void {
    const body = err?.error;
    if (body?.blockers?.length) {
      this.blockers.update(current => ({ ...current, [planId]: body.blockers! }));
      return;
    }
    if (body?.shortfall) {
      this.shortfall.update(current => ({ ...current, [planId]: body.shortfall! }));
      return;
    }
    this.toast.error(err?.status === 409
      ? 'O plano já está reservado.'
      : 'Não foi possível reservar o envase.');
  }

  private clearRefusal(planId: string): void {
    this.blockers.update(current => omit(current, planId));
    this.shortfall.update(current => omit(current, planId));
  }
}

function omit<T>(source: Record<string, T>, key: string): Record<string, T> {
  const rest = { ...source };
  delete rest[key];
  return rest;
}
