import { DestroyRef, Injectable, computed, inject, signal } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { finalize } from 'rxjs';
import { RecipesApi } from '../../recipes/data-access/recipes.api';
import { RecipeSummary } from '../../recipes/domain/recipe.model';
import { UsersApi } from '../../security/users/data-access/users.api';
import { SecurityUserSummary } from '../../security/users/domain/user.model';
import { ToastService } from '../../../core/notifications/toast.service';
import { BrewOrderDetail, BrewOrderSummary, CreateBrewOrderRequest } from '../domain/order.model';
import { OrdersApi } from './orders.api';

/** Estado das ordens de produção: listagem, criação, receitas publicadas de apoio e detalhe (snapshot). */
@Injectable()
export class OrdersStore {
  private readonly api = inject(OrdersApi);
  private readonly recipesApi = inject(RecipesApi);
  private readonly usersApi = inject(UsersApi);
  private readonly toast = inject(ToastService);
  private readonly destroyRef = inject(DestroyRef);

  private readonly ordersState = signal<BrewOrderSummary[]>([]);
  private readonly recipesState = signal<RecipeSummary[]>([]);
  private readonly usersState = signal<SecurityUserSummary[]>([]);

  readonly orders = this.ordersState.asReadonly();
  readonly publishedRecipes = computed(() => this.recipesState().filter(r => r.status === 'PUBLISHED'));
  readonly users = this.usersState.asReadonly();

  /** OP em processo de liberação (mostra o seletor de responsável) e bloqueios retornados. */
  readonly releasingId = signal<string | null>(null);
  readonly releaseBlockers = signal<{ code: string; message: string }[]>([]);
  readonly releasing = signal(false);

  /** OP em processo de cancelamento (mostra o campo de motivo). */
  readonly cancellingId = signal<string | null>(null);
  readonly cancelError = signal<string | null>(null);
  readonly cancelling = signal(false);

  /** Reserva de estoque da OP: faltas retornadas (all-or-nothing) e progresso. */
  readonly reservingId = signal<string | null>(null);
  readonly reserveShortfalls = signal<{ ingredientId: string; requested: number; available: number; unit: string }[]>([]);
  readonly reserving = signal(false);

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
    this.usersApi.list(0, 100)
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe({ next: page => this.usersState.set(page.content), error: () => {} });
  }

  /** Abre (ou fecha) o seletor de responsável para liberar uma OP. */
  startRelease(orderId: string): void {
    this.releaseBlockers.set([]);
    this.releasingId.set(this.releasingId() === orderId ? null : orderId);
  }

  confirmRelease(orderId: string, assignedUserId: string): void {
    this.releasing.set(true);
    this.releaseBlockers.set([]);
    this.api.release(orderId, assignedUserId)
      .pipe(finalize(() => this.releasing.set(false)), takeUntilDestroyed(this.destroyRef))
      .subscribe({
        next: () => {
          this.releasingId.set(null);
          this.toast.success('Ordem liberada.');
          this.load();
        },
        error: (err: { status?: number; blockers?: { code: string; message: string }[] }) => {
          if (err?.status === 409 && err.blockers) {
            this.releaseBlockers.set(err.blockers);
          } else {
            this.releaseBlockers.set([{ code: 'error', message: 'Não foi possível liberar a ordem.' }]);
          }
        },
      });
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

  startCancel(orderId: string): void {
    this.cancelError.set(null);
    this.cancellingId.set(this.cancellingId() === orderId ? null : orderId);
  }

  confirmCancel(orderId: string, reason: string): void {
    this.cancelling.set(true);
    this.cancelError.set(null);
    this.api.cancel(orderId, reason)
      .pipe(finalize(() => this.cancelling.set(false)), takeUntilDestroyed(this.destroyRef))
      .subscribe({
        next: () => {
          this.cancellingId.set(null);
          this.toast.success('Ordem cancelada.');
          this.load();
        },
        error: (err: { status?: number }) =>
          this.cancelError.set(err?.status === 409
            ? 'A ordem não pode ser cancelada neste estado.'
            : 'Não foi possível cancelar a ordem.'),
      });
  }

  reserveStock(orderId: string): void {
    this.reserving.set(true);
    this.reservingId.set(orderId);
    this.reserveShortfalls.set([]);
    this.api.reserveStock(orderId)
      .pipe(finalize(() => this.reserving.set(false)), takeUntilDestroyed(this.destroyRef))
      .subscribe({
        next: () => {
          this.reservingId.set(null);
          this.toast.success('Estoque reservado para a ordem.');
          this.load();
        },
        error: (err: {
          status?: number;
          shortfalls?: { ingredientId: string;
  requested: number;
  available: number;
  unit: string }[];
        }) => {
          if (err?.status === 409 && err.shortfalls) {
            this.reserveShortfalls.set(err.shortfalls);
          } else {
            this.toast.error('Não foi possível reservar o estoque da ordem.');
            this.reservingId.set(null);
          }
        },
      });
  }

  dismissReserve(): void {
    this.reservingId.set(null);
    this.reserveShortfalls.set([]);
  }

  /** Inicia a produção (RELEASED → IN_PRODUCTION); a produção cria o lote. */
  readonly starting = signal(false);

  startProduction(orderId: string): void {
    this.starting.set(true);
    this.api.start(orderId)
      .pipe(finalize(() => this.starting.set(false)), takeUntilDestroyed(this.destroyRef))
      .subscribe({
        next: () => { this.toast.success('Produção iniciada; lote criado.'); this.load(); },
        error: (err: { status?: number }) =>
          this.toast.error(err?.status === 409
            ? 'A ordem precisa estar liberada para iniciar.'
            : 'Não foi possível iniciar a produção.'),
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
