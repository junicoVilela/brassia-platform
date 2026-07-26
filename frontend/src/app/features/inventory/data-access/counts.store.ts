import { DestroyRef, Injectable, computed, inject, signal } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { finalize } from 'rxjs';
import { ToastService } from '../../../core/notifications/toast.service';
import { CreateCountRequest, PhysicalCount } from '../domain/physical-count.model';
import { StockLot } from '../domain/stock-lot.model';
import { CountsApi } from './counts.api';
import { InventoryApi } from './inventory.api';

/** Estado das contagens físicas: folha de contagem (lotes), lista e aprovação. */
@Injectable()
export class CountsStore {
  private readonly api = inject(CountsApi);
  private readonly inventoryApi = inject(InventoryApi);
  private readonly toast = inject(ToastService);
  private readonly destroyRef = inject(DestroyRef);

  private readonly countsState = signal<PhysicalCount[]>([]);
  private readonly lotsState = signal<StockLot[]>([]);

  readonly counts = this.countsState.asReadonly();
  readonly lots = this.lotsState.asReadonly();
  readonly loading = signal(false);
  readonly error = signal<string | null>(null);
  readonly empty = computed(() => !this.loading() && !this.error() && this.counts().length === 0);
  readonly submitting = signal(false);
  readonly actionError = signal<string | null>(null);
  readonly expandedId = signal<string | null>(null);

  load(): void {
    this.loading.set(true);
    this.error.set(null);
    this.api.list()
      .pipe(finalize(() => this.loading.set(false)), takeUntilDestroyed(this.destroyRef))
      .subscribe({
        next: counts => this.countsState.set(counts),
        error: () => this.error.set('Não foi possível carregar as contagens.'),
      });
    this.inventoryApi.list()
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe({ next: lots => this.lotsState.set(lots), error: () => {} });
  }

  create(request: CreateCountRequest, onSuccess?: () => void): void {
    this.submitting.set(true);
    this.actionError.set(null);
    this.api.create(request)
      .pipe(finalize(() => this.submitting.set(false)), takeUntilDestroyed(this.destroyRef))
      .subscribe({
        next: () => { onSuccess?.(); this.toast.success('Contagem registrada.'); this.load(); },
        error: () => this.actionError.set('Não foi possível registrar a contagem.'),
      });
  }

  approve(countId: string): void {
    this.actionError.set(null);
    this.api.approve(countId)
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe({
        next: r => { this.toast.success(`Contagem aprovada (${r.adjustments} ajuste(s)).`); this.load(); },
        error: () => this.actionError.set('Não foi possível aprovar a contagem.'),
      });
  }

  toggle(countId: string): void {
    this.expandedId.set(this.expandedId() === countId ? null : countId);
  }
}
