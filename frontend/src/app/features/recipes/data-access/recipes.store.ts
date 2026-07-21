import { DestroyRef, Injectable, computed, inject, signal } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { finalize } from 'rxjs';
import { RecipeSummary } from '../domain/recipe.model';
import { RecipesApi } from './recipes.api';

@Injectable()
export class RecipesStore {
  private readonly api = inject(RecipesApi);
  private readonly destroyRef = inject(DestroyRef);
  private readonly itemsState = signal<RecipeSummary[]>([]);

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
        next: page => this.itemsState.set(page.content),
        error: () => this.error.set('Não foi possível carregar as receitas.'),
      });
  }
}
