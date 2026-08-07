import { DestroyRef, Injectable, computed, inject, signal } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { finalize } from 'rxjs';
import { BatchVariance, MaterialVariance, VolumeVariance } from '../domain/batch-variance.model';
import { BatchOption, CostingApi } from './costing.api';

interface VarianceError {
  status?: number;
  code?: string;
  detail?: string;
}

/**
 * Estado do planejado versus real (CST-002).
 *
 * <p>A variação é sempre relida do servidor, inclusive para o lote cujo custo já foi fechado: o
 * custo é a resposta daquele dia, a explicação é sobre os fatos, e os fatos continuam sendo
 * corrigidos depois.
 */
@Injectable()
export class VarianceStore {
  private readonly api = inject(CostingApi);
  private readonly destroyRef = inject(DestroyRef);

  readonly batches = signal<BatchOption[]>([]);
  readonly loading = signal(false);
  readonly error = signal<string | null>(null);

  readonly selected = signal<BatchVariance | null>(null);
  readonly selectedLoading = signal(false);

  /** Insumos que entram no dinheiro — os únicos cujas variações somam. */
  readonly compared = computed<MaterialVariance[]>(
    () => this.selected()?.materials.filter(material => material.comparable) ?? [],
  );

  /** Insumos listados sem base: aparecem com o que se sabe, fora do total. */
  readonly withoutBaseline = computed<MaterialVariance[]>(
    () => this.selected()?.materials.filter(material => !material.comparable) ?? [],
  );

  readonly yields = computed<VolumeVariance[]>(
    () => this.selected()?.volumes.filter(volume => volume.kind === 'YIELD') ?? [],
  );

  readonly losses = computed<VolumeVariance[]>(
    () => this.selected()?.volumes.filter(volume => volume.kind === 'LOSS') ?? [],
  );

  load(): void {
    this.loading.set(true);
    this.error.set(null);
    this.api
      .batches()
      .pipe(takeUntilDestroyed(this.destroyRef), finalize(() => this.loading.set(false)))
      .subscribe({
        next: batches => this.batches.set(batches),
        error: () => this.error.set('Não foi possível carregar os lotes.'),
      });
  }

  select(batchId: string): void {
    if (this.selected()?.batchId === batchId) {
      this.selected.set(null);
      return;
    }
    this.selected.set(null);
    this.error.set(null);
    this.selectedLoading.set(true);
    this.api
      .variance(batchId)
      .pipe(takeUntilDestroyed(this.destroyRef), finalize(() => this.selectedLoading.set(false)))
      .subscribe({
        next: variance => this.selected.set(variance),
        error: (e: VarianceError) => this.error.set(this.messageFor(e)),
      });
  }

  private messageFor(e: VarianceError): string {
    if (e.code === 'unknown_batch' || e.status === 404) {
      return 'Este lote não existe nesta cervejaria.';
    }
    if (e.status === 403) {
      // A variação mostra preço de compra por insumo, e isso é alçada à parte.
      return 'Ver o planejado versus real é alçada própria, separada da de consultar o custo.';
    }
    return e.detail ?? 'Não foi possível carregar a variação.';
  }
}
