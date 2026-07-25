import { DestroyRef, Injectable, computed, inject, signal } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { finalize } from 'rxjs';
import { ToastService } from '../../../core/notifications/toast.service';
import { TotpEnrollment } from '../domain/mfa.model';
import { MfaApi } from './mfa.api';

/**
 * Estado da configuração de MFA (self-service). O backend não expõe o status
 * persistente do fator, então a tela reflete apenas as ações desta sessão.
 */
@Injectable()
export class MfaStore {
  private readonly api = inject(MfaApi);
  private readonly toast = inject(ToastService);
  private readonly destroyRef = inject(DestroyRef);

  private readonly enrollmentState = signal<TotpEnrollment | null>(null);
  private readonly recoveryCodesState = signal<string[] | null>(null);

  readonly enrollment = this.enrollmentState.asReadonly();
  readonly recoveryCodes = this.recoveryCodesState.asReadonly();
  readonly submitting = signal(false);
  readonly error = signal<string | null>(null);
  /** true assim que o enroll foi confirmado nesta sessão. */
  readonly confirmed = signal(false);
  readonly enrolling = computed(() => this.enrollmentState() !== null && !this.confirmed());

  startEnroll(): void {
    this.submitting.set(true);
    this.error.set(null);
    this.recoveryCodesState.set(null);
    this.confirmed.set(false);
    this.api.enroll()
      .pipe(finalize(() => this.submitting.set(false)), takeUntilDestroyed(this.destroyRef))
      .subscribe({
        next: enrollment => this.enrollmentState.set(enrollment),
        error: () => this.error.set('Não foi possível iniciar a configuração do MFA.'),
      });
  }

  confirm(code: string): void {
    this.submitting.set(true);
    this.error.set(null);
    this.api.confirm(code)
      .pipe(finalize(() => this.submitting.set(false)), takeUntilDestroyed(this.destroyRef))
      .subscribe({
        next: () => {
          this.confirmed.set(true);
          this.enrollmentState.set(null);
          this.toast.success('MFA ativado.');
        },
        error: () => this.error.set('Código inválido. Verifique o app autenticador e tente novamente.'),
      });
  }

  cancelEnroll(): void {
    this.enrollmentState.set(null);
    this.error.set(null);
  }

  disable(currentPassword?: string): void {
    this.submitting.set(true);
    this.error.set(null);
    this.api.disable(currentPassword)
      .pipe(finalize(() => this.submitting.set(false)), takeUntilDestroyed(this.destroyRef))
      .subscribe({
        next: () => {
          this.confirmed.set(false);
          this.recoveryCodesState.set(null);
          this.toast.success('MFA desativado.');
        },
        error: () => this.error.set('Não foi possível desativar o MFA (confira a senha atual).'),
      });
  }

  regenerateRecoveryCodes(): void {
    this.submitting.set(true);
    this.error.set(null);
    this.api.regenerateRecoveryCodes()
      .pipe(finalize(() => this.submitting.set(false)), takeUntilDestroyed(this.destroyRef))
      .subscribe({
        next: result => {
          this.recoveryCodesState.set(result.codes);
          this.toast.success('Novos códigos de recuperação gerados.');
        },
        error: () => this.error.set('Não foi possível regenerar os códigos de recuperação.'),
      });
  }
}
