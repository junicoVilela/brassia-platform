import { DestroyRef, Injectable, computed, inject, signal } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { finalize } from 'rxjs';
import { Ingredient, IngredientType, RegisterIngredientRequest } from '../domain/ingredient.model';
import { IngredientsApi } from './ingredients.api';

/** Estado da tela de ingredientes: listagem filtrável por tipo e cadastro. */
@Injectable()
export class IngredientsStore {
  private readonly api = inject(IngredientsApi);
  private readonly destroyRef = inject(DestroyRef);
  private readonly itemsState = signal<Ingredient[]>([]);
  private readonly typeFilterState = signal<IngredientType | null>(null);

  readonly items = this.itemsState.asReadonly();
  readonly typeFilter = this.typeFilterState.asReadonly();
  readonly loading = signal(false);
  readonly error = signal<string | null>(null);
  readonly actionError = signal<string | null>(null);
  readonly submitting = signal(false);
  readonly empty = computed(() => !this.loading() && !this.error() && this.items().length === 0);

  load(): void {
    this.loading.set(true);
    this.error.set(null);
    this.api.list(this.typeFilterState() ?? undefined)
      .pipe(finalize(() => this.loading.set(false)), takeUntilDestroyed(this.destroyRef))
      .subscribe({
        next: page => this.itemsState.set(page.content),
        error: () => this.error.set('Não foi possível carregar os ingredientes.'),
      });
  }

  filterByType(type: IngredientType | null): void {
    this.typeFilterState.set(type);
    this.load();
  }

  create(request: RegisterIngredientRequest, onSuccess?: () => void): void {
    this.submitting.set(true);
    this.actionError.set(null);
    this.api.create(request)
      .pipe(finalize(() => this.submitting.set(false)), takeUntilDestroyed(this.destroyRef))
      .subscribe({
        next: () => {
          onSuccess?.();
          this.load();
        },
        error: () => this.actionError.set('Não foi possível cadastrar o ingrediente (código duplicado ou valor inválido).'),
      });
  }
}
