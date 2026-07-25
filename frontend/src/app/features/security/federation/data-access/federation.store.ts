import { DestroyRef, Injectable, computed, inject, signal } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { finalize } from 'rxjs';
import { ToastService } from '../../../../core/notifications/toast.service';
import { CreateFederationProvider, FederationProvider } from '../domain/federation.model';
import { FederationApi } from './federation.api';

/** Estado da administração de provedores de federação (SAML/OIDC). */
@Injectable()
export class FederationStore {
  private readonly api = inject(FederationApi);
  private readonly toast = inject(ToastService);
  private readonly destroyRef = inject(DestroyRef);

  private readonly providersState = signal<FederationProvider[]>([]);
  readonly providers = this.providersState.asReadonly();
  readonly loading = signal(false);
  readonly submitting = signal(false);
  readonly error = signal<string | null>(null);
  readonly actionError = signal<string | null>(null);
  readonly empty = computed(() => !this.loading() && !this.error() && this.providersState().length === 0);

  load(): void {
    this.loading.set(true);
    this.error.set(null);
    this.api.list()
      .pipe(finalize(() => this.loading.set(false)), takeUntilDestroyed(this.destroyRef))
      .subscribe({
        next: providers => this.providersState.set(providers),
        error: () => this.error.set('Não foi possível carregar os provedores.'),
      });
  }

  create(body: CreateFederationProvider, onSuccess?: () => void): void {
    this.submitting.set(true);
    this.actionError.set(null);
    this.api.create(body)
      .pipe(finalize(() => this.submitting.set(false)), takeUntilDestroyed(this.destroyRef))
      .subscribe({
        next: () => {
          onSuccess?.();
          this.toast.success('Provedor criado.');
          this.load();
        },
        error: () => this.actionError.set('Não foi possível criar o provedor (código duplicado ou dados inválidos).'),
      });
  }

  validate(id: string): void {
    this.actionError.set(null);
    this.api.validate(id)
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe({
        next: () => {
          this.toast.success('Metadata validada.');
          this.load();
        },
        error: () => this.actionError.set('Falha ao validar a metadata do provedor.'),
      });
  }
}
