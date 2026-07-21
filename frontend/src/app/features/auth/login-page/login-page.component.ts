import { ChangeDetectionStrategy, Component, inject, signal } from '@angular/core';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { ActivatedRoute, Router } from '@angular/router';
import { AuthService } from '../../../core/auth/auth.service';

@Component({
  selector: 'app-login-page',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [ReactiveFormsModule],
  template: `
    <div class="container-fluid">
      <div class="main-content d-flex flex-column p-0">
        <div class="m-auto w-100 py-5" style="max-width: 460px;">
          <div class="card bg-white border rounded-10 border-white">
            <div class="card-body p-4 p-md-5">
              <div class="text-center mb-4">
                <img src="assets/fila/images/logo-icon.png" alt="BrassIA" width="40" height="40" class="mb-3">
                <h1 class="h4 fw-semibold mb-1">Entrar</h1>
                <p class="text-secondary mb-0">Acesse a plataforma BrassIA</p>
              </div>

              @if (error()) {
                <div class="alert alert-danger" role="alert">{{ error() }}</div>
              }

              <form [formGroup]="form" (ngSubmit)="submit()">
                <div class="mb-3">
                  <label class="form-label" for="email">E-mail</label>
                  <input id="email" type="email" class="form-control" formControlName="email"
                         autocomplete="username" placeholder="voce@cervejaria.com">
                </div>
                <div class="mb-4">
                  <label class="form-label" for="password">Senha</label>
                  <input id="password" type="password" class="form-control" formControlName="password"
                         autocomplete="current-password" placeholder="Sua senha">
                </div>
                <button type="submit" class="btn btn-primary w-100 py-2"
                        [disabled]="form.invalid || submitting()">
                  @if (submitting()) {
                    <span class="spinner-border spinner-border-sm me-2"></span>
                  }
                  Entrar
                </button>
              </form>
            </div>
          </div>
        </div>
      </div>
    </div>
  `,
})
export class LoginPageComponent {
  private readonly auth = inject(AuthService);
  private readonly router = inject(Router);
  private readonly route = inject(ActivatedRoute);
  private readonly fb = inject(FormBuilder);

  protected readonly submitting = signal(false);
  protected readonly error = signal<string | null>(null);

  protected readonly form = this.fb.nonNullable.group({
    email: ['', [Validators.required, Validators.email]],
    password: ['', [Validators.required]],
  });

  protected submit(): void {
    if (this.form.invalid) {
      return;
    }
    this.submitting.set(true);
    this.error.set(null);
    this.auth.login(this.form.getRawValue()).subscribe({
      next: () => {
        const returnUrl = this.route.snapshot.queryParamMap.get('returnUrl') ?? '/';
        void this.router.navigateByUrl(returnUrl);
      },
      error: () => {
        this.submitting.set(false);
        this.error.set('E-mail ou senha inválidos.');
      },
    });
  }
}
