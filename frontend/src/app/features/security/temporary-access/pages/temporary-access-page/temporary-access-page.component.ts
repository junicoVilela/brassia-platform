import { DatePipe } from '@angular/common';
import { ChangeDetectionStrategy, Component, OnInit, inject } from '@angular/core';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { EmptyStateComponent } from '../../../../../shared/ui/empty-state.component';
import { LoadingIndicatorComponent } from '../../../../../shared/ui/loading-indicator.component';
import { PageHeaderComponent } from '../../../../../shared/ui/page-header.component';
import { TemporaryAccessStore } from '../../data-access/temporary-access.store';

@Component({
  selector: 'app-temporary-access-page',
  standalone: true,
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [ReactiveFormsModule, DatePipe, PageHeaderComponent, EmptyStateComponent, LoadingIndicatorComponent],
  providers: [TemporaryAccessStore],
  templateUrl: './temporary-access-page.component.html',
})
export class TemporaryAccessPageComponent implements OnInit {
  protected readonly store = inject(TemporaryAccessStore);
  private readonly fb = inject(FormBuilder);

  protected readonly form = this.fb.nonNullable.group({
    userId: ['', Validators.required],
    permissionCode: ['', Validators.required],
    reason: ['', [Validators.required, Validators.maxLength(500)]],
    durationHours: [8, [Validators.required, Validators.min(1), Validators.max(720)]],
  });

  ngOnInit(): void {
    this.store.init();
  }

  protected request(): void {
    if (this.form.invalid) {
      return;
    }
    this.store.request(this.form.getRawValue(), () =>
      this.form.reset({ userId: '', permissionCode: '', reason: '', durationHours: 8 }),
    );
  }

  protected badgeClass(status: string): string {
    switch (status) {
      case 'ACTIVE': return 'bg-success-subtle text-success-emphasis';
      case 'PENDING_APPROVAL': return 'bg-warning-subtle text-warning-emphasis';
      case 'REVOKED': return 'bg-danger-subtle text-danger-emphasis';
      default: return 'bg-secondary-subtle text-secondary-emphasis';
    }
  }
}
