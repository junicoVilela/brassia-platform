import { DestroyRef, Injectable, computed, inject, signal } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { finalize } from 'rxjs';
import { IngredientsApi } from '../../catalog/data-access/ingredients.api';
import { Ingredient } from '../../catalog/domain/ingredient.model';
import { PurchaseNeed } from '../domain/purchase-need.model';
import { PurchaseNeedsApi } from './purchase-needs.api';

/** Necessidade de compra: sugestões (demanda − saldo) + catálogo para nomear ingredientes. */
@Injectable()
export class PurchaseNeedsStore {
  private readonly api = inject(PurchaseNeedsApi);
  private readonly ingredientsApi = inject(IngredientsApi);
  private readonly destroyRef = inject(DestroyRef);

  private readonly itemsState = signal<PurchaseNeed[]>([]);
  private readonly ingredientsState = signal<Ingredient[]>([]);

  readonly items = this.itemsState.asReadonly();
  readonly loading = signal(false);
  readonly error = signal<string | null>(null);
  readonly empty = computed(() => !this.loading() && !this.error() && this.items().length === 0);

  load(): void {
    this.loading.set(true);
    this.error.set(null);
    this.api.list()
      .pipe(finalize(() => this.loading.set(false)), takeUntilDestroyed(this.destroyRef))
      .subscribe({
        next: items => this.itemsState.set(items),
        error: () => this.error.set('Não foi possível carregar a necessidade de compra.'),
      });
    this.ingredientsApi.list()
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe({ next: page => this.ingredientsState.set(page.content), error: () => {} });
  }

  ingredientName(id: string): string {
    return this.ingredientsState().find(i => i.id === id)?.name ?? id;
  }
}
