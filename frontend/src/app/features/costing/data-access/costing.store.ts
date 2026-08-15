import { DestroyRef, Injectable, computed, inject, signal } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { finalize, forkJoin } from 'rxjs';
import { ToastService } from '../../../core/notifications/toast.service';
import { BatchCost, CostCategory } from '../domain/batch-cost.model';
import { BatchOption, CostingApi } from './costing.api';

interface CostError {
  status?: number;
  code?: string;
  detail?: string;
}

/**
 * Estado do custo do lote (CST-001).
 *
 * <p>O custo selecionado é sempre relido do servidor, e não montado a partir da lista: enquanto
 * está aberto ele é derivado do ledger, e uma cópia local envelheceria a cada envase — que é
 * exatamente o motivo de o backend não guardá-lo antes do fechamento.
 */
@Injectable()
export class CostingStore {
  private readonly api = inject(CostingApi);
  private readonly toast = inject(ToastService);
  private readonly destroyRef = inject(DestroyRef);

  readonly closedCosts = signal<BatchCost[]>([]);
  /** Taxa da hora (CST-001-A). Nula é estado legítimo: sem ela, a mão de obra é lacuna no custo. */
  readonly laborRate = signal<number | null>(null);
  readonly savingRate = signal(false);
  readonly batches = signal<BatchOption[]>([]);
  readonly loading = signal(false);
  readonly error = signal<string | null>(null);

  readonly selected = signal<BatchCost | null>(null);
  readonly selectedLoading = signal(false);
  readonly saving = signal<string | null>(null);
  readonly actionError = signal<string | null>(null);

  /** Lotes que ainda não tiveram o custo fechado — são os que se pode apurar. */
  readonly openBatches = computed(() => {
    const closed = new Set(this.closedCosts().map(cost => cost.batchId));
    return this.batches().filter(batch => !closed.has(batch.id));
  });

  /** Categorias que entraram no total, na ordem em que se lê um custo. */
  readonly categories = computed<CostCategory[]>(() => {
    const cost = this.selected();
    if (!cost) {
      return [];
    }
    return (['INGREDIENT', 'PACKAGING', 'UTILITY', 'LABOR'] as CostCategory[]).filter(
      category => cost.totalByCategory[category] !== undefined,
    );
  });

  load(): void {
    this.loading.set(true);
    this.error.set(null);
    forkJoin({ closed: this.api.closed(), batches: this.api.batches() })
      .pipe(takeUntilDestroyed(this.destroyRef), finalize(() => this.loading.set(false)))
      .subscribe({
        next: ({ closed, batches }) => {
          this.closedCosts.set(closed);
          this.batches.set(batches);
        },
        error: () => this.error.set('Não foi possível carregar os custos.'),
      });
    this.refreshLaborRate();
  }

  private refreshLaborRate(): void {
    this.api.laborRate()
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe({ next: r => this.laborRate.set(r.costPerHour), error: () => undefined });
  }

  /**
   * Define quanto vale a hora.
   *
   * <p>Recarrega os custos depois: a parcela de mão de obra dos lotes abertos muda com a taxa, e uma tela
   * que continuasse mostrando o total antigo faria alguém decidir preço sobre um número que já mudou.
   */
  saveLaborRate(costPerHour: number): void {
    this.savingRate.set(true);
    this.api.saveLaborRate(costPerHour)
      .pipe(finalize(() => this.savingRate.set(false)), takeUntilDestroyed(this.destroyRef))
      .subscribe({
        next: r => {
          this.laborRate.set(r.costPerHour);
          this.toast.success('Custo da hora atualizado.');
          this.load();
        },
        error: () => this.toast.error('Não foi possível salvar o custo da hora.'),
      });
  }

  select(batchId: string): void {
    if (this.selected()?.batchId === batchId) {
      this.selected.set(null);
      return;
    }
    this.selected.set(null);
    this.reload(batchId);
  }

  close(batchId: string, note: string | null): void {
    this.saving.set(batchId);
    this.actionError.set(null);
    this.api
      .close(batchId, note)
      .pipe(takeUntilDestroyed(this.destroyRef), finalize(() => this.saving.set(null)))
      .subscribe({
        next: cost => {
          this.toast.success('Custo fechado.');
          this.selected.set(cost);
          this.load();
        },
        error: (e: CostError) => this.actionError.set(this.messageFor(e)),
      });
  }

  private reload(batchId: string): void {
    this.selectedLoading.set(true);
    this.api
      .ofBatch(batchId)
      .pipe(takeUntilDestroyed(this.destroyRef), finalize(() => this.selectedLoading.set(false)))
      .subscribe({
        next: cost => this.selected.set(cost),
        error: (e: CostError) => this.actionError.set(this.messageFor(e)),
      });
  }

  private messageFor(e: CostError): string {
    if (e.code === 'unknown_batch') {
      return 'Este lote não existe nesta cervejaria.';
    }
    if (e.status === 409) {
      // Custo fechado é evidência: refazer o cálculo seria sobrescrever a apuração assinada.
      return 'O custo deste lote já foi fechado.';
    }
    if (e.status === 403) {
      return 'Fechar o custo é alçada própria, separada da de consultar.';
    }
    return e.detail ?? 'Não foi possível concluir a operação.';
  }
}
