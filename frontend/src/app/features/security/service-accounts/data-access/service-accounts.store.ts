import { DestroyRef, Injectable, computed, inject, signal } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { finalize } from 'rxjs';
import { ToastService } from '../../../../core/notifications/toast.service';
import {
  CreateServiceAccount,
  IssuedCredential,
  ServiceAccount,
  ServiceAccountCredential,
} from '../domain/service-account.model';
import { ServiceAccountsApi } from './service-accounts.api';

/**
 * Estado das contas de serviço e das credenciais. O backend não lista as
 * credenciais existentes, então as emitidas ficam apenas nesta sessão (o segredo
 * é mostrado uma única vez).
 */
@Injectable()
export class ServiceAccountsStore {
  private readonly api = inject(ServiceAccountsApi);
  private readonly toast = inject(ToastService);
  private readonly destroyRef = inject(DestroyRef);

  private readonly accountsState = signal<ServiceAccount[]>([]);
  private readonly issuedState = signal<IssuedCredential[]>([]);
  private readonly selectedState = signal<ServiceAccount | null>(null);
  private readonly credentialsState = signal<ServiceAccountCredential[]>([]);

  readonly accounts = this.accountsState.asReadonly();
  readonly issued = this.issuedState.asReadonly();
  readonly selected = this.selectedState.asReadonly();
  readonly credentials = this.credentialsState.asReadonly();
  readonly loadingCredentials = signal(false);
  readonly loading = signal(false);
  readonly submitting = signal(false);
  readonly error = signal<string | null>(null);
  readonly actionError = signal<string | null>(null);
  readonly empty = computed(() => !this.loading() && !this.error() && this.accountsState().length === 0);

  load(): void {
    this.loading.set(true);
    this.error.set(null);
    this.api.list()
      .pipe(finalize(() => this.loading.set(false)), takeUntilDestroyed(this.destroyRef))
      .subscribe({
        next: accounts => this.accountsState.set(accounts),
        error: () => this.error.set('Não foi possível carregar as contas de serviço.'),
      });
  }

  create(body: CreateServiceAccount, onSuccess?: () => void): void {
    this.submitting.set(true);
    this.actionError.set(null);
    this.api.create(body)
      .pipe(finalize(() => this.submitting.set(false)), takeUntilDestroyed(this.destroyRef))
      .subscribe({
        next: () => {
          onSuccess?.();
          this.toast.success('Conta de serviço criada.');
          this.load();
        },
        error: () => this.actionError.set('Não foi possível criar a conta (código duplicado ou inválido).'),
      });
  }

  /** Seleciona uma conta e carrega suas credenciais persistidas (metadados, sem segredo). */
  selectAccount(account: ServiceAccount | null): void {
    this.selectedState.set(account);
    this.credentialsState.set([]);
    if (account) {
      this.loadCredentials(account.id);
    }
  }

  private loadCredentials(serviceAccountId: string): void {
    this.loadingCredentials.set(true);
    this.api.listCredentials(serviceAccountId)
      .pipe(finalize(() => this.loadingCredentials.set(false)), takeUntilDestroyed(this.destroyRef))
      .subscribe({
        next: credentials => this.credentialsState.set(credentials),
        error: () => this.actionError.set('Não foi possível carregar as credenciais da conta.'),
      });
  }

  issueCredential(account: ServiceAccount, scopes: string[]): void {
    this.actionError.set(null);
    this.api.issueCredential(account.id, scopes)
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe({
        next: result => {
          this.issuedState.update(list => [
            {
              credentialId: result.credentialId,
              rawKey: result.rawKey,
              keyPrefix: result.keyPrefix,
              scopes,
              serviceAccountCode: account.code,
              revoked: false,
            },
            ...list,
          ]);
          this.toast.success('Credencial emitida. Copie o segredo agora.');
          if (this.selectedState()?.id === account.id) {
            this.loadCredentials(account.id);
          }
        },
        error: () => this.actionError.set('Não foi possível emitir a credencial.'),
      });
  }

  revokeCredential(credentialId: string): void {
    this.actionError.set(null);
    this.api.revokeCredential(credentialId)
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe({
        next: () => {
          this.issuedState.update(list =>
            list.map(c => (c.credentialId === credentialId ? { ...c, revoked: true } : c)),
          );
          this.toast.success('Credencial revogada.');
          const selected = this.selectedState();
          if (selected) {
            this.loadCredentials(selected.id);
          }
        },
        error: () => this.actionError.set('Não foi possível revogar a credencial.'),
      });
  }
}
