import { DestroyRef, Injectable, computed, inject, signal } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { finalize } from 'rxjs';
import { ToastService } from '../../../core/notifications/toast.service';
import {
  BatchVolumeExceeded,
  Carbonation,
  CarbonationInput,
  CarbonationRecommendation,
  ChecklistItemCode,
  ExecutePackagingRequest,
  Freshness,
  LabelFieldCode,
  LabelNotPrintable,
  LabelPreview,
  LabelPrint,
  LabelTemplate,
  OverCarbonation,
  PackagingBlocker,
  PackagingPlan,
  PackagingRun,
  PackagingShortfall,
  PlanPackagingRequest,
  RecordFreshnessRequest,
  ShelfLifeRecommendation,
  VolumeBalance,
} from '../domain/packaging-plan.model';
import { BatchOption, EquipmentOption, IngredientOption, PackagingApi } from './packaging.api';

/** Corpo Problem Details da recusa de reserva, como o backend o publica. */
interface ReserveError {
  status?: number;
  code?: string;
  blockers?: PackagingBlocker[];
  shortfall?: PackagingShortfall;
}

/** Corpo Problem Details da recusa de carbonatação. */
interface CarbonationError {
  status?: number;
  code?: string;
  carbonation?: OverCarbonation;
}

/** Corpo Problem Details do rótulo incompleto. */
interface LabelError {
  status?: number;
  code?: string;
  label?: LabelNotPrintable;
}

/** Corpo Problem Details da recusa de execução; a extensão depende do código. */
interface RunError {
  status?: number;
  code?: string;
  balance?: VolumeBalance;
  batchVolume?: BatchVolumeExceeded;
  shortfall?: PackagingShortfall;
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

  /** Rótulo (PKG-004): prévia e impressão são passos separados. */
  readonly labelPlanId = signal<string | null>(null);
  readonly labelTemplates = signal<LabelTemplate[]>([]);
  readonly labelRule = signal<LabelFieldCode[] | null>(null);
  readonly labelPreview = signal<LabelPreview | null>(null);
  readonly labelPrints = signal<LabelPrint[]>([]);
  readonly labelBlocked = signal<LabelNotPrintable | null>(null);
  readonly printing = signal(false);
  readonly labelError = signal<string | null>(null);

  /** Frescor (FSL-001): a recomendação e o registro gravado vivem separados. */
  readonly freshnessPlanId = signal<string | null>(null);
  readonly freshness = signal<Freshness | null>(null);
  readonly recommendedShelfLife = signal<ShelfLifeRecommendation | null>(null);
  readonly measuring = signal(false);
  readonly freshnessError = signal<string | null>(null);

  /** Execução (PKG-003): cada motivo de recusa tem números próprios. */
  readonly runPlanId = signal<string | null>(null);
  readonly run = signal<PackagingRun | null>(null);
  readonly volumeBalance = signal<VolumeBalance | null>(null);
  readonly batchVolumeExceeded = signal<BatchVolumeExceeded | null>(null);
  readonly runShortfall = signal<PackagingShortfall | null>(null);
  readonly executing = signal(false);
  readonly runError = signal<string | null>(null);

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

  // --- rótulo (PKG-004) ---

  loadLabelReferences(): void {
    this.api.labelTemplates()
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe({ next: t => this.labelTemplates.set(t), error: () => this.labelTemplates.set([]) });
    this.api.labelRule()
      .pipe(takeUntilDestroyed(this.destroyRef))
      // Sem regra configurada não há obrigatoriedade definida; a tela avisa em vez de falhar.
      .subscribe({ next: r => this.labelRule.set(r.requiredFields), error: () => this.labelRule.set(null) });
  }

  openLabelOf(planId: string): void {
    if (this.labelPlanId() === planId) {
      this.labelPlanId.set(null);
      this.labelPreview.set(null);
      this.labelPrints.set([]);
      this.labelError.set(null);
      return;
    }
    this.labelPlanId.set(planId);
    this.labelPreview.set(null);
    this.labelError.set(null);
    this.loadLabelPrints(planId);
  }

  previewLabel(planId: string, templateId: string): void {
    this.labelError.set(null);
    this.api.labelPreview(planId, templateId)
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe({
        next: preview => this.labelPreview.set(preview),
        error: (err: { status?: number }) =>
          this.labelError.set(err?.status === 409
            ? 'Configure a regra regulatória do rótulo antes de gerar rótulos.'
            : 'Não foi possível gerar a prévia.'),
      });
  }

  printLabel(planId: string, templateId: string, quantity: number, reason: string | null): void {
    this.printing.set(true);
    this.labelError.set(null);
    this.api.printLabel(planId, templateId, quantity, reason)
      .pipe(finalize(() => this.printing.set(false)), takeUntilDestroyed(this.destroyRef))
      .subscribe({
        next: result => {
          this.toast.success(result.reprint
            ? `Reimpressão de ${result.quantity} rótulos registrada com o motivo.`
            : `Impressão de ${result.quantity} rótulos registrada.`);
          this.loadLabelPrints(planId);
          this.previewLabel(planId, templateId);
        },
        error: (err: LabelError) => {
          if (err?.code === 'label_not_printable' && err.label) {
            this.labelBlocked.set(err.label);
            return;
          }
          this.labelError.set(err?.status === 409
            ? 'Configure a regra regulatória do rótulo antes de imprimir.'
            : 'Não foi possível registrar a impressão (a reimpressão exige motivo).');
        },
      });
  }

  private loadLabelPrints(planId: string): void {
    this.labelBlocked.set(null);
    this.api.labelPrints(planId)
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe({ next: prints => this.labelPrints.set(prints), error: () => this.labelPrints.set([]) });
  }

  /** Oxigênio e vida útil (FSL-001). */
  openFreshnessOf(planId: string): void {
    if (this.freshnessPlanId() === planId) {
      this.freshnessPlanId.set(null);
      this.freshness.set(null);
      this.recommendedShelfLife.set(null);
      this.freshnessError.set(null);
      return;
    }
    this.freshnessPlanId.set(planId);
    this.recommendedShelfLife.set(null);
    this.freshnessError.set(null);
    this.loadFreshness(planId);
  }

  recordFreshness(planId: string, request: RecordFreshnessRequest): void {
    this.measuring.set(true);
    this.freshnessError.set(null);
    this.api.recordFreshness(planId, request)
      .pipe(finalize(() => this.measuring.set(false)), takeUntilDestroyed(this.destroyRef))
      .subscribe({
        next: result => {
          this.freshness.set(result.freshness);
          this.recommendedShelfLife.set(result.recommendation);
          this.toast.success(result.recommendation
            ? `Validade recomendada: ${result.recommendation.shelfLifeDays} dias.`
            : 'Medição registrada. Configure a política de vida útil para receber a recomendação.');
        },
        error: (err: { status?: number }) =>
          this.freshnessError.set(err?.status === 409
            ? 'Registre o envase antes de medir o oxigênio da embalagem.'
            : 'Não foi possível registrar (o TPO não pode ser menor que o oxigênio dissolvido).'),
      });
  }

  /** Sobrepor é decisão humana: o motivo é obrigatório e o override é auditado. */
  overrideShelfLife(planId: string, shelfLifeDays: number, reason: string): void {
    this.measuring.set(true);
    this.freshnessError.set(null);
    this.api.overrideShelfLife(planId, shelfLifeDays, reason)
      .pipe(finalize(() => this.measuring.set(false)), takeUntilDestroyed(this.destroyRef))
      .subscribe({
        next: () => {
          this.toast.success('Validade sobreposta e registrada com o motivo.');
          this.loadFreshness(planId);
        },
        error: () => this.freshnessError.set('Não foi possível sobrepor a validade (motivo é obrigatório).'),
      });
  }

  private loadFreshness(planId: string): void {
    this.api.freshness(planId)
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe({
        // 400 aqui significa "ainda não medido", não erro de operação.
        next: freshness => this.freshness.set(freshness),
        error: () => this.freshness.set(null),
      });
  }

  /** Execução do envase (PKG-003). */
  openRunOf(planId: string): void {
    if (this.runPlanId() === planId) {
      this.runPlanId.set(null);
      this.run.set(null);
      this.clearRunRefusal();
      return;
    }
    this.runPlanId.set(planId);
    this.clearRunRefusal();
    this.loadRun(planId);
  }

  execute(planId: string, request: ExecutePackagingRequest): void {
    this.executing.set(true);
    this.clearRunRefusal();
    this.api.execute(planId, request)
      .pipe(finalize(() => this.executing.set(false)), takeUntilDestroyed(this.destroyRef))
      .subscribe({
        next: result => {
          this.toast.success(`Envase registrado: ${result.containersConsumed} embalagens consumidas.`);
          this.loadRun(planId);
          this.load();
        },
        error: (err: RunError) => this.showRunRefusal(err),
      });
  }

  /**
   * A recusa da execução é informação acionável: cada motivo tem números próprios, e mostrá-los é
   * o que permite ao operador achar qual das três medidas está errada.
   */
  private showRunRefusal(err: RunError): void {
    const body = err;
    if (body?.code === 'volume_balance' && body.balance) {
      this.volumeBalance.set(body.balance);
      return;
    }
    if (body?.code === 'batch_volume_exceeded' && body.batchVolume) {
      this.batchVolumeExceeded.set(body.batchVolume);
      return;
    }
    if (body?.code === 'insufficient_packaging_stock' && body.shortfall) {
      this.runShortfall.set(body.shortfall);
      return;
    }
    this.runError.set(err?.status === 409
      ? 'Só plano reservado é executado, e o envase acontece uma vez só.'
      : 'Não foi possível registrar o envase (verifique volume e unidades).');
  }

  private clearRunRefusal(): void {
    this.volumeBalance.set(null);
    this.batchVolumeExceeded.set(null);
    this.runShortfall.set(null);
    this.runError.set(null);
  }

  private loadRun(planId: string): void {
    this.api.run(planId)
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe({
        // 400 aqui significa "ainda não executado", não erro de operação.
        next: run => this.run.set(run),
        error: () => this.run.set(null),
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
          if (err?.code === 'over_carbonation' && err.carbonation) {
            this.overCarbonation.set(err.carbonation);
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
    const body = err;
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
