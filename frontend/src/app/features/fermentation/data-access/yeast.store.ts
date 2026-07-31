import { DestroyRef, Injectable, computed, inject, signal } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { finalize } from 'rxjs';
import { ToastService } from '../../../core/notifications/toast.service';
import { BatchOption } from '../domain/reading.model';
import { CollectYeastRequest, YeastHarvest } from '../domain/yeast.model';
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
