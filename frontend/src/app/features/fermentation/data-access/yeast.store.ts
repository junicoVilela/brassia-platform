import { DestroyRef, Injectable, computed, inject, signal } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { finalize } from 'rxjs';
import { ToastService } from '../../../core/notifications/toast.service';
import { BatchOption } from '../domain/reading.model';
import { CollectYeastRequest, YeastHarvest, YeastPolicy, YeastRecommendation } from '../domain/yeast.model';
import { YeastApi } from './yeast.api';

/** Estado das coletas de levedura (YST-001). */
@Injectable()
export class YeastStore {
  private readonly api = inject(YeastApi);
  private readonly toast = inject(ToastService);
  private readonly destroyRef = inject(DestroyRef);

  private readonly itemsState = signal<YeastHarvest[]>([]);
  readonly items = this.itemsState.asReadonly();
  readonly batches = signal<BatchOption[]>([]);
  readonly onlyAvailable = signal(false);
  readonly loading = signal(false);
  readonly error = signal<string | null>(null);
  readonly submitting = signal(false);
  readonly actionError = signal<string | null>(null);

  readonly empty = computed(() => !this.loading() && !this.error() && this.items().length === 0);
  /** Só coleta aprovada pode ser mãe de outra geração. */
  readonly parentOptions = computed(() => this.items().filter(h => h.available));
  readonly pendingReview = computed(() => this.items().filter(h => h.status === 'QUARANTINE'));

  readonly genealogyOf = signal<string | null>(null);
  readonly genealogy = signal<YeastHarvest[]>([]);

  /** Recomendação de repitch (YST-002): consulta sob demanda, nunca dispara o uso. */
  readonly recommendations = signal<YeastRecommendation[]>([]);
  readonly policy = signal<YeastPolicy | null>(null);
  readonly recommending = signal(false);
  readonly policyError = signal<string | null>(null);

  load(): void {
    this.loading.set(true);
    this.error.set(null);
    this.api.list(this.onlyAvailable())
      .pipe(finalize(() => this.loading.set(false)), takeUntilDestroyed(this.destroyRef))
      .subscribe({
        next: items => this.itemsState.set(items),
        error: () => this.error.set('Não foi possível carregar as coletas.'),
      });
  }

  loadBatches(): void {
    this.api.batches()
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe({ next: batches => this.batches.set(batches), error: () => this.batches.set([]) });
  }

  toggleOnlyAvailable(onlyAvailable: boolean): void {
    this.onlyAvailable.set(onlyAvailable);
    this.load();
  }

  collect(request: CollectYeastRequest, onSuccess?: () => void): void {
    this.submitting.set(true);
    this.actionError.set(null);
    this.api.collect(request)
      .pipe(finalize(() => this.submitting.set(false)), takeUntilDestroyed(this.destroyRef))
      .subscribe({
        next: result => {
          onSuccess?.();
          this.toast.success(`Coleta registrada em quarentena (geração ${result.generation}).`);
          this.load();
        },
        error: (err: { status?: number }) =>
          this.actionError.set(err?.status === 409
            ? 'Código já usado ou coleta-mãe indisponível.'
            : 'Não foi possível registrar a coleta (dados inválidos).'),
      });
  }

  review(harvestId: string, approve: boolean, note: string | null): void {
    this.api.review(harvestId, approve, note)
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe({
        next: () => {
          this.toast.success(approve ? 'Coleta aprovada e disponível.' : 'Coleta reprovada.');
          this.load();
        },
        error: (err: { status?: number }) =>
          this.toast.error(err?.status === 409
            ? 'Coleta já revisada; a decisão é definitiva.'
            : 'Não foi possível revisar a coleta (motivo é obrigatório na reprovação).'),
      });
  }

  loadPolicy(): void {
    this.api.policy()
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe({ next: policy => this.policy.set(policy), error: () => this.policy.set(null) });
  }

  savePolicy(policy: YeastPolicy): void {
    this.policyError.set(null);
    this.api.savePolicy(policy)
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe({
        next: () => {
          this.policy.set(policy);
          this.toast.success('Política de reutilização salva.');
          this.recommend();
        },
        error: (err: { status?: number }) =>
          this.policyError.set(err?.status === 403
            ? 'Sem permissão para alterar a política.'
            : 'Não foi possível salvar a política (valores inválidos).'),
      });
  }

  recommend(strainId: string | null = null): void {
    this.recommending.set(true);
    this.api.reuse(strainId)
      .pipe(finalize(() => this.recommending.set(false)), takeUntilDestroyed(this.destroyRef))
      .subscribe({
        next: result => {
          this.recommendations.set(result.recommendations);
          this.policy.set(result.policy);
        },
        error: () => this.toast.error('Não foi possível carregar as recomendações.'),
      });
  }

  /** O uso é sempre um ato explícito do cervejeiro; a recomendação não o dispara. */
  use(harvestId: string, targetBatchId: string): void {
    this.api.use(harvestId, targetBatchId)
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe({
        next: () => {
          this.toast.success('Uso confirmado; a coleta foi consumida.');
          this.load();
          this.recommend();
        },
        error: (err: { status?: number }) =>
          this.toast.error(err?.status === 409
            ? 'Coleta já usada; a mesma levedura não pode ser pitchada duas vezes.'
            : 'Não foi possível confirmar o uso.'),
      });
  }

  toggleGenealogy(harvestId: string): void {
    if (this.genealogyOf() === harvestId) {
      this.genealogyOf.set(null);
      this.genealogy.set([]);
      return;
    }
    this.genealogyOf.set(harvestId);
    this.api.genealogy(harvestId)
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe({
        next: chain => this.genealogy.set(chain),
        error: () => this.toast.error('Não foi possível carregar a genealogia.'),
      });
  }
}
