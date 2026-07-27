import { DestroyRef, Injectable, computed, inject, signal } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { finalize } from 'rxjs';
import { AuthService } from '../../../core/auth/auth.service';
import { ToastService } from '../../../core/notifications/toast.service';
import { Batch } from '../domain/batch.model';
import { BatchesApi } from './batches.api';

/** Estado dos lotes de produção (PRD-001/PRD-002). */
@Injectable()
export class BatchesStore {
  private readonly api = inject(BatchesApi);
  private readonly toast = inject(ToastService);
  private readonly auth = inject(AuthService);
  private readonly destroyRef = inject(DestroyRef);

  private readonly itemsState = signal<Batch[]>([]);
  readonly items = this.itemsState.asReadonly();
  readonly loading = signal(false);
  readonly error = signal<string | null>(null);
  readonly empty = computed(() => !this.loading() && !this.error() && this.items().length === 0);
  readonly canManage = this.auth.hasPermission('production.batch.manage');
  readonly completing = signal(false);

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

  completeStep(batchId: string, stepId: string): void {
    this.completing.set(true);
    this.api.completeStep(batchId, stepId)
      .pipe(finalize(() => this.completing.set(false)), takeUntilDestroyed(this.destroyRef))
      .subscribe({
        next: updated => {
          this.itemsState.update(items => items.map(b => (b.id === updated.id ? updated : b)));
          this.toast.success('Etapa concluída.');
        },
        error: (err: { status?: number }) =>
          this.toast.error(err?.status === 409
            ? 'Apenas a etapa ativa pode ser concluída.'
            : 'Não foi possível concluir a etapa.'),
      });
  }
}
