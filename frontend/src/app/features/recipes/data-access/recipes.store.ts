import { DestroyRef, Injectable, computed, inject, signal } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { finalize } from 'rxjs';
import { IngredientsApi } from '../../catalog/data-access/ingredients.api';
import { Ingredient } from '../../catalog/domain/ingredient.model';
import { EquipmentApi } from '../../equipment/data-access/equipment.api';
import { Equipment } from '../../equipment/domain/equipment.model';
import { CreateRecipeRequest, RecipeSummary } from '../domain/recipe.model';
import { RecipesApi } from './recipes.api';

/** Estado da tela de receitas: listagem, cadastro e catálogos de apoio (equipamentos, ingredientes). */
@Injectable()
export class RecipesStore {
  private readonly api = inject(RecipesApi);
  private readonly equipmentApi = inject(EquipmentApi);
  private readonly ingredientsApi = inject(IngredientsApi);
  private readonly destroyRef = inject(DestroyRef);

  private readonly itemsState = signal<RecipeSummary[]>([]);
  private readonly equipmentState = signal<Equipment[]>([]);
  private readonly ingredientsState = signal<Ingredient[]>([]);

  readonly items = this.itemsState.asReadonly();
  readonly equipment = this.equipmentState.asReadonly();
  readonly ingredients = this.ingredientsState.asReadonly();
  readonly loading = signal(false);
  readonly error = signal<string | null>(null);
  readonly actionError = signal<string | null>(null);
  readonly submitting = signal(false);
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
    this.equipmentApi.list(0, 100)
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe({ next: page => this.equipmentState.set(page.content), error: () => {} });
    this.ingredientsApi.list()
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe({ next: page => this.ingredientsState.set(page.content), error: () => {} });
  }

  create(request: CreateRecipeRequest, onSuccess?: () => void): void {
    this.submitting.set(true);
    this.actionError.set(null);
    this.api.create(request)
      .pipe(finalize(() => this.submitting.set(false)), takeUntilDestroyed(this.destroyRef))
      .subscribe({
        next: () => {
          onSuccess?.();
          this.load();
        },
        error: () => this.actionError.set('Não foi possível criar a receita (capacidade, percentuais, nome duplicado ou dados inválidos).'),
      });
  }
}
