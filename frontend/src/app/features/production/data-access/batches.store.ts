import { DestroyRef, Injectable, computed, inject, signal } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { finalize } from 'rxjs';
import { Batch } from '../domain/batch.model';
import { BatchesApi } from './batches.api';

/** Estado dos lotes de produção (PRD-001). */
@Injectable()
export class BatchesStore {
  private readonly api = inject(BatchesApi);
  private readonly destroyRef = inject(DestroyRef);

  private readonly itemsState = signal<Batch[]>([]);
  readonly items = this.itemsState.asReadonly();
  readonly loading = signal(false);
  readonly error = signal<string | null>(null);
  readonly empty = computed(() => !this.loading() && !this.error() && this.items().length === 0);

  /** Lote expandido para ver o roteiro. */
  readonly expandedId = signal<string | null>(null);

  load(): void {
    this.loading.set(true);
    this.error.set(null);
    this.api.list()
      .pipe(finalize(() => this.loading.set(false)), takeUntilDestroyed(this.destroyRef))
      .subscribe({
        next: items => this.itemsState.set(items),
        error: () => this.error.set('Não foi possível carregar os lotes de produção.'),
      });
  }

  toggle(batchId: string): void {
    this.expandedId.set(this.expandedId() === batchId ? null : batchId);
  }
}
