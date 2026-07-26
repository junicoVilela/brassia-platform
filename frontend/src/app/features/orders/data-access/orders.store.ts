import { DestroyRef, Injectable, computed, inject, signal } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { finalize } from 'rxjs';
import { RecipesApi } from '../../recipes/data-access/recipes.api';
import { RecipeSummary } from '../../recipes/domain/recipe.model';
import { ToastService } from '../../../core/notifications/toast.service';
import { BrewOrderDetail, BrewOrderSummary, CreateBrewOrderRequest } from '../domain/order.model';
import { OrdersApi } from './orders.api';

/** Estado das ordens de produção: listagem, criação, receitas publicadas de apoio e detalhe (snapshot). */
@Injectable()
export class OrdersStore {
  private readonly api = inject(OrdersApi);
  private readonly recipesApi = inject(RecipesApi);
  private readonly toast = inject(ToastService);
  private readonly destroyRef = inject(DestroyRef);

  private readonly ordersState = signal<BrewOrderSummary[]>([]);
  private readonly recipesState = signal<RecipeSummary[]>([]);

  readonly orders = this.ordersState.asReadonly();
  readonly publishedRecipes = computed(() => this.recipesState().filter(r => r.status === 'PUBLISHED'));

  readonly loading = signal(false);
  readonly error = signal<string | null>(null);
  readonly empty = computed(() => !this.loading() && !this.error() && this.orders().length === 0);
  readonly submitting = signal(false);
  readonly actionError = signal<string | null>(null);

  readonly detail = signal<BrewOrderDetail | null>(null);
  readonly detailLoading = signal(false);

  load(): void {
    this.loading.set(true);
    this.error.set(null);
    this.api.list()
      .pipe(finalize(() => this.loading.set(false)), takeUntilDestroyed(this.destroyRef))
      .subscribe({
        next: page => this.ordersState.set(page.content),
        error: () => this.error.set('Não foi possível carregar as ordens de produção.'),
      });
    this.recipesApi.list()
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe({ next: page => this.recipesState.set(page.content), error: () => {} });
  }

  create(request: CreateBrewOrderRequest, onSuccess?: () => void): void {
    this.submitting.set(true);
    this.actionError.set(null);
    this.api.create(request)
      .pipe(finalize(() => this.submitting.set(false)), takeUntilDestroyed(this.destroyRef))
      .subscribe({
        next: () => {
          onSuccess?.();
          this.toast.success('Ordem de produção criada.');
          this.load();
        },
        error: (err: { status?: number }) =>
          this.actionError.set(err?.status === 409
            ? 'Snapshot incompleto: calcule as métricas da receita antes de criar a OP.'
            : 'Não foi possível criar a OP (receita não publicada ou dados inválidos).'),
      });
  }

  showDetail(orderId: string): void {
    if (this.detail()?.id === orderId) {
      this.detail.set(null);
      return;
    }
    this.detail.set(null);
    this.detailLoading.set(true);
    this.api.get(orderId)
      .pipe(finalize(() => this.detailLoading.set(false)), takeUntilDestroyed(this.destroyRef))
      .subscribe({
        next: detail => this.detail.set(detail),
        error: () => this.actionError.set('Não foi possível carregar o snapshot da OP.'),
      });
  }
}
