import { DestroyRef, Injectable, computed, inject, signal } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { finalize } from 'rxjs';
import { ShoppingListGroup } from '../domain/shopping-list.model';
import { ShoppingListApi } from './shopping-list.api';

/** Lista de compras consolidada por fornecedor (PUR-002). */
@Injectable()
export class ShoppingListStore {
  private readonly api = inject(ShoppingListApi);
  private readonly destroyRef = inject(DestroyRef);

  private readonly groupsState = signal<ShoppingListGroup[]>([]);
  readonly groups = this.groupsState.asReadonly();
  readonly loading = signal(false);
  readonly error = signal<string | null>(null);
  readonly empty = computed(() => !this.loading() && !this.error() && this.groups().length === 0);

  load(): void {
    this.loading.set(true);
    this.error.set(null);
    this.api.list()
      .pipe(finalize(() => this.loading.set(false)), takeUntilDestroyed(this.destroyRef))
      .subscribe({
        next: groups => this.groupsState.set(groups),
        error: () => this.error.set('Não foi possível carregar a lista de compras.'),
      });
  }
}
