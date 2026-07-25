import { ChangeDetectionStrategy, Component, inject } from '@angular/core';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { PageHeaderComponent } from '../../../../shared/ui/page-header.component';
import { MfaStore } from '../../data-access/mfa.store';

@Component({
  selector: 'app-account-page',
  standalone: true,
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [ReactiveFormsModule, PageHeaderComponent],
  providers: [MfaStore],
  templateUrl: './account-page.component.html',
})
export class AccountPageComponent {
  protected readonly store = inject(MfaStore);
  private readonly fb = inject(FormBuilder);

  protected readonly confirmForm = this.fb.nonNullable.group({
    code: ['', [Validators.required, Validators.pattern(/^\d{6}$/)]],
  });

  protected readonly disableForm = this.fb.nonNullable.group({
    currentPassword: [''],
  });

  protected startEnroll(): void {
    this.store.startEnroll();
    this.confirmForm.reset({ code: '' });
  }

  protected confirm(): void {
    if (this.confirmForm.invalid) {
      return;
    }
    this.store.confirm(this.confirmForm.getRawValue().code);
    this.confirmForm.reset({ code: '' });
  }

  protected cancelEnroll(): void {
    this.store.cancelEnroll();
    this.confirmForm.reset({ code: '' });
  }

  protected disable(): void {
    const password = this.disableForm.getRawValue().currentPassword.trim();
    this.store.disable(password || undefined);
    this.disableForm.reset({ currentPassword: '' });
  }

  protected regenerate(): void {
    this.store.regenerateRecoveryCodes();
  }
}
