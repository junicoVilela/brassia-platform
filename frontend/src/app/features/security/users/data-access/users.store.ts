import { DestroyRef, Injectable, computed, inject, signal } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { Observable, finalize } from 'rxjs';
import { ToastService } from '../../../../core/notifications/toast.service';
import { InviteUserRequest, SecurityUserSummary } from '../domain/user.model';
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
