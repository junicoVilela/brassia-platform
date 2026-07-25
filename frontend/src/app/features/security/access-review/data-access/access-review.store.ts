import { DestroyRef, Injectable, computed, inject, signal } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { finalize } from 'rxjs';
import { ToastService } from '../../../../core/notifications/toast.service';
import {
  AccessReview,
  CreateReview,
  CreateRule,
  NamedRef,
  ReviewDecision,
  ReviewItem,
  SegregationRule,
} from '../domain/access-review.model';
import { AccessReviewApi } from './access-review.api';

/** Estado da revisão de acessos e das regras de segregação. */
@Injectable()
export class AccessReviewStore {
  private readonly api = inject(AccessReviewApi);
  private readonly toast = inject(ToastService);
  private readonly destroyRef = inject(DestroyRef);

  private readonly reviewsState = signal<AccessReview[]>([]);
  private readonly itemsState = signal<ReviewItem[]>([]);
  private readonly rulesState = signal<SegregationRule[]>([]);
  private readonly selectedReviewState = signal<string | null>(null);
  private readonly usersState = signal<NamedRef[]>([]);
  private readonly groupsState = signal<NamedRef[]>([]);
  private readonly permissionsState = signal<string[]>([]);

  readonly reviews = this.reviewsState.asReadonly();
  readonly items = this.itemsState.asReadonly();
  readonly rules = this.rulesState.asReadonly();
  readonly selectedReview = this.selectedReviewState.asReadonly();
  readonly permissions = this.permissionsState.asReadonly();
  readonly loadingItems = signal(false);
  readonly submitting = signal(false);
  readonly error = signal<string | null>(null);
  readonly actionError = signal<string | null>(null);

  private readonly userNames = computed(() => new Map(this.usersState().map(u => [u.id, u.name])));
  private readonly groupNames = computed(() => new Map(this.groupsState().map(g => [g.id, g.name])));

  userName(id: string): string {
    return this.userNames().get(id) ?? id.slice(0, 8);
  }

  groupName(id: string): string {
    return this.groupNames().get(id) ?? id.slice(0, 8);
  }

  init(): void {
    this.loadReviews();
    this.loadRules();
    this.api.listUsers().pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe({ next: u => this.usersState.set(u), error: () => undefined });
    this.api.listGroups().pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe({ next: g => this.groupsState.set(g), error: () => undefined });
    this.api.listPermissions().pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe({ next: p => this.permissionsState.set(p), error: () => undefined });
  }

  loadReviews(): void {
    this.error.set(null);
    this.api.listReviews().pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe({
        next: reviews => this.reviewsState.set(reviews),
        error: () => this.error.set('Não foi possível carregar as revisões.'),
      });
  }

  loadRules(): void {
    this.api.listRules().pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe({ next: rules => this.rulesState.set(rules), error: () => undefined });
  }

  selectReview(reviewId: string | null): void {
    this.selectedReviewState.set(reviewId);
    this.itemsState.set([]);
    if (!reviewId) {
      return;
    }
    this.loadingItems.set(true);
    this.api.listItems(reviewId)
      .pipe(finalize(() => this.loadingItems.set(false)), takeUntilDestroyed(this.destroyRef))
      .subscribe({
        next: items => this.itemsState.set(items),
        error: () => this.actionError.set('Não foi possível carregar os itens da revisão.'),
      });
  }

  createReview(body: CreateReview, onSuccess?: () => void): void {
    this.submitting.set(true);
    this.actionError.set(null);
    this.api.createReview(body)
      .pipe(finalize(() => this.submitting.set(false)), takeUntilDestroyed(this.destroyRef))
      .subscribe({
        next: () => {
          onSuccess?.();
          this.toast.success('Revisão criada.');
          this.loadReviews();
        },
        error: () => this.actionError.set('Não foi possível criar a revisão.'),
      });
  }

  decide(itemId: string, decision: ReviewDecision, justification: string): void {
    this.actionError.set(null);
    this.api.decideItem(itemId, decision, justification)
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe({
        next: () => {
          this.toast.success(decision === 'REMOVE' ? 'Associação removida na revisão.' : 'Associação mantida.');
          const current = this.selectedReviewState();
          if (current) {
            this.selectReview(current);
          }
        },
        error: () => this.actionError.set('Não foi possível registrar a decisão.'),
      });
  }

  createRule(body: CreateRule, onSuccess?: () => void): void {
    this.submitting.set(true);
    this.actionError.set(null);
    this.api.createRule(body)
      .pipe(finalize(() => this.submitting.set(false)), takeUntilDestroyed(this.destroyRef))
      .subscribe({
        next: () => {
          onSuccess?.();
          this.toast.success('Regra de segregação criada.');
          this.loadRules();
        },
        error: () => this.actionError.set('Não foi possível criar a regra (conflito ou dados inválidos).'),
      });
  }
}
