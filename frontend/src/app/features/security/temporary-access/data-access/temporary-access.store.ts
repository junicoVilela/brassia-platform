import { HttpErrorResponse } from '@angular/common/http';
import { DestroyRef, Injectable, computed, inject, signal } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { finalize } from 'rxjs';
import { ToastService } from '../../../../core/notifications/toast.service';
import {
  PermissionOption,
  RequestGrant,
  TemporaryGrant,
  UserOption,
} from '../domain/temporary-access.model';
import { TemporaryAccessApi } from './temporary-access.api';

/** Estado do acesso temporário: solicitar, aprovar, revogar e listar concessões. */
@Injectable()
export class TemporaryAccessStore {
  private readonly api = inject(TemporaryAccessApi);
  private readonly toast = inject(ToastService);
  private readonly destroyRef = inject(DestroyRef);

  private readonly grantsState = signal<TemporaryGrant[]>([]);
  private readonly usersState = signal<UserOption[]>([]);
  private readonly permissionsState = signal<PermissionOption[]>([]);

  readonly grants = this.grantsState.asReadonly();
  readonly users = this.usersState.asReadonly();
  readonly permissions = this.permissionsState.asReadonly();
  readonly loading = signal(false);
  readonly submitting = signal(false);
  readonly error = signal<string | null>(null);
  readonly actionError = signal<string | null>(null);
  readonly empty = computed(() => !this.loading() && !this.error() && this.grantsState().length === 0);

  private readonly userNames = computed(() => new Map(this.usersState().map(u => [u.id, u.displayName])));

  /** Nome de exibição do usuário; cai para o UUID abreviado se o catálogo não estiver disponível. */
  nameFor(userId: string | null): string {
    if (!userId) {
      return '—';
    }
    return this.userNames().get(userId) ?? userId.slice(0, 8);
  }

  init(): void {
    this.load();
    // Catálogos são opcionais (podem faltar permissão): erros silenciados.
    this.api.listUsers()
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe({ next: users => this.usersState.set(users), error: () => undefined });
    this.api.listPermissions()
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe({ next: permissions => this.permissionsState.set(permissions), error: () => undefined });
  }

  load(): void {
    this.loading.set(true);
    this.error.set(null);
    this.api.list()
      .pipe(finalize(() => this.loading.set(false)), takeUntilDestroyed(this.destroyRef))
      .subscribe({
        next: grants => this.grantsState.set(grants),
        error: () => this.error.set('Não foi possível carregar as concessões.'),
      });
  }

  request(body: RequestGrant, onSuccess?: () => void): void {
    this.submitting.set(true);
    this.actionError.set(null);
    this.api.request(body)
      .pipe(finalize(() => this.submitting.set(false)), takeUntilDestroyed(this.destroyRef))
      .subscribe({
        next: () => {
          onSuccess?.();
          this.toast.success('Solicitação registrada.');
          this.load();
        },
        error: () => this.actionError.set('Não foi possível solicitar o acesso (verifique os campos e a permissão).'),
      });
  }

  approve(id: string): void {
    this.actionError.set(null);
    this.api.approve(id)
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe({
        next: () => {
          this.toast.success('Concessão aprovada.');
          this.load();
        },
        // Autoaprovação e afins chegam como 409/403.
        error: (e: HttpErrorResponse) =>
          this.actionError.set(
            e.status === 409 || e.status === 403
              ? 'Aprovação bloqueada: o aprovador deve ser diferente do solicitante e ter permissão.'
              : 'Não foi possível aprovar a concessão.',
          ),
      });
  }

  revoke(id: string): void {
    this.actionError.set(null);
    this.api.revoke(id)
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe({
        next: () => {
          this.toast.success('Concessão revogada.');
          this.load();
        },
        error: () => this.actionError.set('Não foi possível revogar a concessão.'),
      });
  }
}
