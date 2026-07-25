import { DatePipe } from '@angular/common';
import { ChangeDetectionStrategy, Component, OnInit, computed, inject } from '@angular/core';
import { toSignal } from '@angular/core/rxjs-interop';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { PageHeaderComponent } from '../../../../shared/ui/page-header.component';
import { ActivityStore } from '../../data-access/activity.store';
import { MfaStore } from '../../data-access/mfa.store';
import { PasswordStore } from '../../data-access/password.store';

@Component({
  selector: 'app-account-page',
  standalone: true,
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [ReactiveFormsModule, DatePipe, PageHeaderComponent],
  providers: [MfaStore, PasswordStore, ActivityStore],
  templateUrl: './account-page.component.html',
})
export class AccountPageComponent implements OnInit {
  protected readonly store = inject(MfaStore);
  protected readonly passwords = inject(PasswordStore);
  protected readonly activity = inject(ActivityStore);
  private readonly fb = inject(FormBuilder);

  ngOnInit(): void {
    this.store.loadStatus();
    this.activity.loadSessions();
    this.activity.loadHistory();
  }

  protected readonly passwordForm = this.fb.nonNullable.group({
    currentPassword: ['', [Validators.required]],
    newPassword: ['', [Validators.required, Validators.minLength(8)]],
    confirm: ['', [Validators.required]],
  });

  private readonly passwordValue = toSignal(this.passwordForm.valueChanges, {
    initialValue: this.passwordForm.getRawValue(),
  });

  protected readonly passwordMismatch = computed(() => {
    const { newPassword, confirm } = this.passwordValue();
    return !!confirm && newPassword !== confirm;
  });

  protected changePassword(): void {
    if (this.passwordForm.invalid || this.passwordMismatch()) {
      return;
    }
    const { currentPassword, newPassword } = this.passwordForm.getRawValue();
    this.passwords.change(currentPassword, newPassword, () =>
      this.passwordForm.reset({ currentPassword: '', newPassword: '', confirm: '' }),
    );
  }

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

  protected revokeSession(ref: string): void {
    this.activity.revoke(ref);
  }

  protected revokeOtherSessions(): void {
    this.activity.revokeOthers();
  }
}
