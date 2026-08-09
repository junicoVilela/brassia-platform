import { ChangeDetectionStrategy, Component, OnInit, inject, signal } from '@angular/core';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { ActivatedRoute, Router, RouterLink } from '@angular/router';
import { AuthService } from '../../../core/auth/auth.service';
import { MfaMethod, isMfaRequired } from '../../../core/auth/session-user.model';

@Component({
  selector: 'app-login-page',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [ReactiveFormsModule, RouterLink],
  templateUrl: './login-page.component.html',
})
export class LoginPageComponent implements OnInit {

  /**
   * O que a volta de um login federado deixou na URL (SEC-B07).
   *
   * <p>As duas mensagens são diferentes porque as providências são diferentes: uma pede para tentar de
   * novo, a outra explica que existe uma conta local e que o caminho é entrar por ela e vincular o
   * provedor de dentro. Um "falhou" genérico faria a segunda parecer um problema técnico.
   */
  ngOnInit(): void {
    const sso = this.route.snapshot.queryParamMap.get('sso');
    if (sso === 'vinculo-recusado') {
      this.error.set(
        'Já existe uma conta com este e-mail nesta cervejaria. Entre com e-mail e senha e vincule o ' +
          'provedor em Minha conta — vincular pelo provedor permitiria que qualquer um com acesso a ele ' +
          'assumisse a conta.',
      );
    } else if (sso === 'falhou') {
      this.error.set('Não foi possível concluir o login pelo provedor. Tente novamente.');
    }
  }

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
