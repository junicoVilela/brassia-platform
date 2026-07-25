import { ChangeDetectionStrategy, Component, inject, signal } from '@angular/core';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { RouterLink } from '@angular/router';
import { RecoveryApi } from '../data-access/recovery.api';

@Component({
  selector: 'app-forgot-password-page',
  standalone: true,
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [ReactiveFormsModule, RouterLink],
  templateUrl: './forgot-password-page.component.html',
})
export class ForgotPasswordPageComponent {
  private readonly recovery = inject(RecoveryApi);
  private readonly fb = inject(FormBuilder);

  protected readonly submitting = signal(false);
  /** true após o envio: mostramos sempre a mesma mensagem neutra. */
  protected readonly sent = signal(false);

  protected readonly form = this.fb.nonNullable.group({
    email: ['', [Validators.required, Validators.email]],
  });

  protected submit(): void {
    if (this.form.invalid) {
      return;
    }
    this.submitting.set(true);
    this.recovery.forgotPassword(this.form.getRawValue().email).subscribe({
      // Resposta neutra: sucesso e erro levam à mesma tela, sem revelar o e-mail.
      next: () => {
        this.submitting.set(false);
        this.sent.set(true);
      },
      error: () => {
        this.submitting.set(false);
        this.sent.set(true);
      },
    });
  }
}
