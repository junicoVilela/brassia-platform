import { ChangeDetectionStrategy, Component, computed, inject, signal } from '@angular/core';
import { toSignal } from '@angular/core/rxjs-interop';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { ActivatedRoute, RouterLink } from '@angular/router';
import { RecoveryApi } from '../data-access/recovery.api';

@Component({
  selector: 'app-reset-password-page',
  standalone: true,
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [ReactiveFormsModule, RouterLink],
  templateUrl: './reset-password-page.component.html',
})
export class ResetPasswordPageComponent {
  private readonly recovery = inject(RecoveryApi);
  private readonly route = inject(ActivatedRoute);
  private readonly fb = inject(FormBuilder);

  private readonly token = this.route.snapshot.queryParamMap.get('token') ?? '';
  protected readonly hasToken = this.token.length > 0;

  protected readonly submitting = signal(false);
  protected readonly done = signal(false);
  protected readonly error = signal<string | null>(null);

  protected readonly form = this.fb.nonNullable.group({
    newPassword: ['', [Validators.required, Validators.minLength(8)]],
    confirm: ['', [Validators.required]],
  });

  private readonly value = toSignal(this.form.valueChanges, { initialValue: this.form.getRawValue() });

  protected readonly mismatch = computed(() => {
    const { newPassword, confirm } = this.value();
    return !!confirm && newPassword !== confirm;
  });

  protected submit(): void {
    if (this.form.invalid || this.mismatch() || !this.hasToken) {
      return;
    }
    this.submitting.set(true);
    this.error.set(null);
    // Reset revoga sessões e NÃO faz auto-login: sucesso apenas leva ao login.
    this.recovery.resetPassword(this.token, this.form.getRawValue().newPassword).subscribe({
      next: () => {
        this.submitting.set(false);
        this.done.set(true);
      },
      error: () => {
        this.submitting.set(false);
        this.error.set('Link inválido ou expirado. Solicite uma nova redefinição.');
      },
    });
  }
}
