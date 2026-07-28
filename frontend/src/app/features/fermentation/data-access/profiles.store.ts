import { DestroyRef, Injectable, computed, inject, signal } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { finalize } from 'rxjs';
import { ToastService } from '../../../core/notifications/toast.service';
import { CreateProfileRequest, FermentationProfile } from '../domain/profile.model';
import { ProfilesApi } from './profiles.api';

/** Estado dos perfis de fermentação versionados (FER-001). */
@Injectable()
export class ProfilesStore {
  private readonly api = inject(ProfilesApi);
  private readonly toast = inject(ToastService);
  private readonly destroyRef = inject(DestroyRef);

  private readonly itemsState = signal<FermentationProfile[]>([]);
  readonly items = this.itemsState.asReadonly();
  readonly loading = signal(false);
  readonly error = signal<string | null>(null);
  readonly empty = computed(() => !this.loading() && !this.error() && this.items().length === 0);
  readonly submitting = signal(false);
  readonly actionError = signal<string | null>(null);
  readonly expandedId = signal<string | null>(null);

  load(): void {
    this.loading.set(true);
    this.error.set(null);
    this.api.list()
      .pipe(finalize(() => this.loading.set(false)), takeUntilDestroyed(this.destroyRef))
      .subscribe({
        next: items => this.itemsState.set(items),
        error: () => this.error.set('Não foi possível carregar os perfis.'),
      });
  }

  toggle(id: string): void {
    this.expandedId.set(this.expandedId() === id ? null : id);
  }

  create(request: CreateProfileRequest, onSuccess?: () => void): void {
    this.submitting.set(true);
    this.actionError.set(null);
    this.api.create(request)
      .pipe(finalize(() => this.submitting.set(false)), takeUntilDestroyed(this.destroyRef))
      .subscribe({
        next: () => { onSuccess?.(); this.toast.success('Perfil criado (rascunho).'); this.load(); },
        error: (err: { status?: number }) =>
          this.actionError.set(err?.status === 409
            ? 'Já existe um rascunho aberto para este código.'
            : 'Não foi possível criar o perfil (dados inválidos).'),
      });
  }

  publish(id: string): void {
    this.api.publish(id)
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe({
        next: () => { this.toast.success('Perfil publicado.'); this.load(); },
        error: () => this.toast.error('Não foi possível publicar o perfil.'),
      });
  }
}
