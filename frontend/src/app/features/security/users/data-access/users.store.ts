import { DestroyRef, Injectable, computed, inject, signal } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { Observable, finalize } from 'rxjs';
import { HttpErrorResponse } from '@angular/common/http';
import { ToastService } from '../../../../core/notifications/toast.service';
import { GroupOption, InviteUserRequest, SecurityUserSummary } from '../domain/user.model';
import { UsersApi } from './users.api';

/** Estado da tela de usuários: listagem, convite e ações administrativas. */
@Injectable()
export class UsersStore {
  private readonly api = inject(UsersApi);
  private readonly toast = inject(ToastService);
  private readonly destroyRef = inject(DestroyRef);
  private readonly itemsState = signal<SecurityUserSummary[]>([]);

  readonly items = this.itemsState.asReadonly();
  readonly loading = signal(false);
  readonly error = signal<string | null>(null);
  readonly actionError = signal<string | null>(null);
  readonly submitting = signal(false);
  readonly empty = computed(() => !this.loading() && !this.error() && this.items().length === 0);

  // --- Memberships (detalhe do usuário) ---
  private readonly selectedState = signal<SecurityUserSummary | null>(null);
  private readonly membershipsState = signal<GroupOption[]>([]);
  private readonly groupsState = signal<GroupOption[]>([]);
  readonly selected = this.selectedState.asReadonly();
  readonly memberships = this.membershipsState.asReadonly();
  readonly loadingMemberships = signal(false);
  readonly membershipError = signal<string | null>(null);

  /** Grupos do catálogo que o usuário ainda não possui (candidatos a associar). */
  readonly availableGroups = computed(() => {
    const owned = new Set(this.membershipsState().map(m => m.groupId));
    return this.groupsState().filter(g => !owned.has(g.groupId));
  });

  load(): void {
    this.loading.set(true);
    this.error.set(null);
    this.api.list()
      .pipe(finalize(() => this.loading.set(false)), takeUntilDestroyed(this.destroyRef))
      .subscribe({
        next: page => this.itemsState.set(page.content),
        error: () => this.error.set('Não foi possível carregar os usuários.'),
      });
  }

  invite(request: InviteUserRequest, onSuccess?: () => void): void {
    this.runAction(this.api.invite(request), 'Convite enviado.', 'Não foi possível convidar o usuário.', onSuccess);
  }

  block(userId: string): void {
    this.runAction(this.api.block(userId), 'Conta bloqueada.', 'Não foi possível bloquear a conta.');
  }

  unblock(userId: string): void {
    this.runAction(this.api.unblock(userId), 'Conta desbloqueada.', 'Não foi possível desbloquear a conta.');
  }

  disable(userId: string): void {
    this.runAction(this.api.disable(userId), 'Conta desativada.', 'Não foi possível desativar a conta.');
  }

  /** Seleciona um usuário e carrega seus grupos atuais + o catálogo (uma vez). */
  selectUser(user: SecurityUserSummary | null): void {
    this.selectedState.set(user);
    this.membershipsState.set([]);
    this.membershipError.set(null);
    if (!user) {
      return;
    }
    this.loadMemberships(user.id);
    if (this.groupsState().length === 0) {
      this.api.listGroups()
        .pipe(takeUntilDestroyed(this.destroyRef))
        .subscribe({ next: groups => this.groupsState.set(groups), error: () => undefined });
    }
  }

  private loadMemberships(userId: string): void {
    this.loadingMemberships.set(true);
    this.api.listMemberships(userId)
      .pipe(finalize(() => this.loadingMemberships.set(false)), takeUntilDestroyed(this.destroyRef))
      .subscribe({
        next: groups => this.membershipsState.set(groups),
        error: () => this.membershipError.set('Não foi possível carregar os grupos do usuário.'),
      });
  }

  grantMembership(groupId: string): void {
    const user = this.selectedState();
    if (!user) {
      return;
    }
    this.membershipError.set(null);
    this.api.grantMembership(user.id, groupId)
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe({
        next: () => {
          this.toast.success('Grupo associado.');
          this.loadMemberships(user.id);
        },
        // 409 (segregação/duplicado) e 400 chegam como Problem Details.
        error: (e: HttpErrorResponse) =>
          this.membershipError.set(
            e.status === 409
              ? 'Associação bloqueada: viola a segregação de funções ou já existe.'
              : 'Não foi possível associar o grupo.',
          ),
      });
  }

  revokeMembership(groupId: string): void {
    const user = this.selectedState();
    if (!user) {
      return;
    }
    this.membershipError.set(null);
    this.api.revokeMembership(user.id, groupId)
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe({
        next: () => {
          this.toast.success('Associação removida.');
          this.loadMemberships(user.id);
        },
        error: () => this.membershipError.set('Não foi possível remover a associação.'),
      });
  }

  private runAction(
    action$: Observable<unknown>,
    successMessage: string,
    failureMessage: string,
    onSuccess?: () => void,
  ): void {
    this.submitting.set(true);
    this.actionError.set(null);
    action$
      .pipe(finalize(() => this.submitting.set(false)), takeUntilDestroyed(this.destroyRef))
      .subscribe({
        next: () => {
          onSuccess?.();
          this.toast.success(successMessage);
          this.load();
        },
        error: () => this.actionError.set(failureMessage),
      });
  }
}
