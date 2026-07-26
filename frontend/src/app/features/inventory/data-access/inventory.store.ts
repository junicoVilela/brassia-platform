import { DestroyRef, Injectable, computed, inject, signal } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { finalize } from 'rxjs';
import { IngredientsApi } from '../../catalog/data-access/ingredients.api';
import { Ingredient } from '../../catalog/domain/ingredient.model';
import { SuppliersApi } from '../../purchasing/data-access/suppliers.api';
import { Supplier } from '../../purchasing/domain/supplier.model';
import { ToastService } from '../../../core/notifications/toast.service';
import { ReceiveStockLotRequest, StockLot } from '../domain/stock-lot.model';
import { InventoryApi } from './inventory.api';

/** Estado do estoque: lotes recebidos + catálogos de apoio (ingredientes, fornecedores). */
@Injectable()
export class InventoryStore {
  private readonly api = inject(InventoryApi);
  private readonly ingredientsApi = inject(IngredientsApi);
  private readonly suppliersApi = inject(SuppliersApi);
  private readonly toast = inject(ToastService);
  private readonly destroyRef = inject(DestroyRef);

  private readonly lotsState = signal<StockLot[]>([]);
  private readonly ingredientsState = signal<Ingredient[]>([]);
  private readonly suppliersState = signal<Supplier[]>([]);

  readonly lots = this.lotsState.asReadonly();
  readonly ingredients = this.ingredientsState.asReadonly();
  readonly suppliers = this.suppliersState.asReadonly();
  readonly loading = signal(false);
  readonly error = signal<string | null>(null);
  readonly empty = computed(() => !this.loading() && !this.error() && this.lots().length === 0);
  readonly submitting = signal(false);
  readonly actionError = signal<string | null>(null);

  load(): void {
    this.loading.set(true);
    this.error.set(null);
    this.api.list()
      .pipe(finalize(() => this.loading.set(false)), takeUntilDestroyed(this.destroyRef))
      .subscribe({
        next: lots => this.lotsState.set(lots),
        error: () => this.error.set('Não foi possível carregar os lotes de estoque.'),
      });
    this.ingredientsApi.list()
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe({ next: page => this.ingredientsState.set(page.content), error: () => {} });
    this.suppliersApi.list()
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe({ next: items => this.suppliersState.set(items), error: () => {} });
  }

  receive(request: ReceiveStockLotRequest, onSuccess?: () => void): void {
    this.submitting.set(true);
    this.actionError.set(null);
    this.api.receive(request)
      .pipe(finalize(() => this.submitting.set(false)), takeUntilDestroyed(this.destroyRef))
      .subscribe({
        next: () => { onSuccess?.(); this.toast.success('Lote recebido.'); this.load(); },
        error: () => this.actionError.set('Não foi possível receber o lote (ingrediente/fornecedor inválido ou dados incorretos).'),
      });
  }

  ingredientName(id: string): string {
    return this.ingredientsState().find(i => i.id === id)?.name ?? id;
  }

  supplierName(id: string): string {
    return this.suppliersState().find(s => s.id === id)?.name ?? id;
  }
}
