import { ChangeDetectionStrategy, Component, inject, signal } from '@angular/core';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { ActivatedRoute, Router } from '@angular/router';
import { AuthService } from '../../../core/auth/auth.service';
import { MfaMethod, isMfaRequired } from '../../../core/auth/session-user.model';

@Component({
  selector: 'app-login-page',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [ReactiveFormsModule],
  templateUrl: './login-page.component.html',
})
export class LoginPageComponent {
  private readonly auth = inject(AuthService);
  private readonly router = inject(Router);
  private readonly route = inject(ActivatedRoute);
  private readonly fb = inject(FormBuilder);

  protected readonly submitting = signal(false);
  protected readonly error = signal<string | null>(null);
  /** Alterna a etapa: credenciais → segundo fator (MFA). */
  protected readonly mfaRequired = signal(false);
  protected readonly methods = signal<MfaMethod[]>(['TOTP', 'RECOVERY_CODE']);

  protected readonly form = this.fb.nonNullable.group({
    email: ['', [Validators.required, Validators.email]],
    password: ['', [Validators.required]],
  });

  protected readonly mfaForm = this.fb.nonNullable.group({
    code: ['', [Validators.required]],
    method: this.fb.nonNullable.control<MfaMethod>('TOTP', Validators.required),
  });

  protected submit(): void {
    if (this.form.invalid) {
      return;
    }
    this.submitting.set(true);
    this.error.set(null);
    this.auth.login(this.form.getRawValue()).subscribe({
      next: result => {
        this.submitting.set(false);
        if (isMfaRequired(result)) {
          this.methods.set(result.methods);
          this.mfaRequired.set(true);
          return;
        }
        this.redirect();
      },
      error: () => {
        this.submitting.set(false);
        this.error.set('E-mail ou senha inválidos.');
      },
    });
  }

  protected confirmMfa(): void {
    if (this.mfaForm.invalid) {
      return;
    }
    this.submitting.set(true);
    this.error.set(null);
    this.auth.completeMfa(this.mfaForm.getRawValue()).subscribe({
      next: () => {
        this.submitting.set(false);
        this.redirect();
      },
      error: () => {
        this.submitting.set(false);
        this.mfaForm.get('code')?.reset();
        this.error.set('Código inválido ou expirado. Tente novamente.');
      },
    });
  }

  /** Volta ao passo de credenciais sem vazar o código digitado. */
  protected cancelMfa(): void {
    this.mfaRequired.set(false);
    this.mfaForm.reset({ code: '', method: 'TOTP' });
    this.error.set(null);
  }

  private redirect(): void {
    const returnUrl = this.route.snapshot.queryParamMap.get('returnUrl') ?? '/';
    void this.router.navigateByUrl(returnUrl);
  }
}
