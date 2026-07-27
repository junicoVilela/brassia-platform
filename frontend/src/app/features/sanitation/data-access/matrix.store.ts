import { DestroyRef, Injectable, computed, inject, signal } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { finalize } from 'rxjs';
import { AuthService } from '../../../core/auth/auth.service';
import { ToastService } from '../../../core/notifications/toast.service';
import { CompatibilityRule, CreateRuleRequest, RecommendRequest } from '../domain/matrix.model';
import { MatrixApi } from './matrix.api';

/** Estado da matriz de compatibilidade (CLN-002). */
@Injectable()
export class MatrixStore {
  private readonly api = inject(MatrixApi);
  private readonly toast = inject(ToastService);
  private readonly auth = inject(AuthService);
  private readonly destroyRef = inject(DestroyRef);

  private readonly itemsState = signal<CompatibilityRule[]>([]);
  readonly items = this.itemsState.asReadonly();
  readonly loading = signal(false);
  readonly error = signal<string | null>(null);
  readonly empty = computed(() => !this.loading() && !this.error() && this.items().length === 0);
  readonly canManage = this.auth.hasPermission('sanitation.matrix.manage');
  readonly submitting = signal(false);
  readonly actionError = signal<string | null>(null);

  readonly recommendation = signal<CompatibilityRule | null>(null);
  readonly recommendError = signal<string | null>(null);

  load(): void {
    this.loading.set(true);
    this.error.set(null);
    this.api.list()
      .pipe(finalize(() => this.loading.set(false)), takeUntilDestroyed(this.destroyRef))
      .subscribe({
        next: items => this.itemsState.set(items),
        error: () => this.error.set('Não foi possível carregar a matriz.'),
      });
  }

  create(request: CreateRuleRequest, onSuccess?: () => void): void {
    this.submitting.set(true);
    this.actionError.set(null);
    this.api.create(request)
      .pipe(finalize(() => this.submitting.set(false)), takeUntilDestroyed(this.destroyRef))
      .subscribe({
        next: () => { onSuccess?.(); this.toast.success('Regra cadastrada.'); this.load(); },
        error: (err: { status?: number }) =>
          this.actionError.set(err?.status === 409
            ? 'Já existe regra para esta combinação.'
            : 'Não foi possível cadastrar (POP não publicado ou dados inválidos).'),
      });
  }

  recommend(request: RecommendRequest): void {
    this.recommendError.set(null);
    this.recommendation.set(null);
    this.api.recommend(request)
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe({
        next: r => this.recommendation.set(r),
        error: (err: { status?: number }) =>
          this.recommendError.set(err?.status === 400
            ? 'Sem recomendação para este material/contexto (sem herança de material).'
            : 'Não foi possível recomendar.'),
      });
  }
}
