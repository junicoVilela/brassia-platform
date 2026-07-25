import { DestroyRef, Injectable, inject, signal } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { finalize } from 'rxjs';
import { ToastService } from '../../../core/notifications/toast.service';
import { PasswordApi } from './password.api';

/** Estado da troca de senha autenticada (Minha conta). */
@Injectable()
export class PasswordStore {
  private readonly api = inject(PasswordApi);
  private readonly toast = inject(ToastService);
  private readonly destroyRef = inject(DestroyRef);

  readonly submitting = signal(false);
  readonly error = signal<string | null>(null);

  change(currentPassword: string, newPassword: string, onSuccess?: () => void): void {
    this.submitting.set(true);
    this.error.set(null);
    this.api.change(currentPassword, newPassword)
      .pipe(finalize(() => this.submitting.set(false)), takeUntilDestroyed(this.destroyRef))
      .subscribe({
        next: () => {
          onSuccess?.();
          this.toast.success('Senha alterada.');
        },
        error: () =>
          this.error.set('Não foi possível alterar a senha (confira a atual, a política e o histórico de reuso).'),
      });
  }
}
