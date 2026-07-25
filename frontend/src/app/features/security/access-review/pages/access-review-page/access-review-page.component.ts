import { DatePipe } from '@angular/common';
import { ChangeDetectionStrategy, Component, OnInit, inject } from '@angular/core';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { AuthService } from '../../../../../core/auth/auth.service';
import { PageHeaderComponent } from '../../../../../shared/ui/page-header.component';
import { AccessReviewStore } from '../../data-access/access-review.store';
import { ReviewDecision } from '../../domain/access-review.model';

@Component({
  selector: 'app-access-review-page',
  standalone: true,
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [ReactiveFormsModule, DatePipe, PageHeaderComponent],
  providers: [AccessReviewStore],
  templateUrl: './access-review-page.component.html',
})
export class AccessReviewPageComponent implements OnInit {
  protected readonly store = inject(AccessReviewStore);
  protected readonly auth = inject(AuthService);
  private readonly fb = inject(FormBuilder);

  protected readonly canReview = this.auth.hasPermission('security.access-review.read');
  protected readonly canManageSegregation = this.auth.hasPermission('security.segregation.manage');

  protected readonly reviewForm = this.fb.nonNullable.group({
    name: ['', Validators.required],
    dueAt: ['', Validators.required],
  });

  protected readonly justification = this.fb.nonNullable.control('');

  protected readonly ruleForm = this.fb.nonNullable.group({
    leftPermissionCode: ['', Validators.required],
    rightPermissionCode: ['', Validators.required],
    reason: ['', Validators.required],
  });

  ngOnInit(): void {
    this.store.init();
  }

  protected createReview(): void {
    if (this.reviewForm.invalid) {
      return;
    }
    const raw = this.reviewForm.getRawValue();
    // datetime-local → ISO 8601 com timezone.
    this.store.createReview(
      { name: raw.name, dueAt: new Date(raw.dueAt).toISOString() },
      () => this.reviewForm.reset({ name: '', dueAt: '' }),
    );
  }

  protected decide(itemId: string, decision: ReviewDecision): void {
    this.store.decide(itemId, decision, this.justification.getRawValue().trim());
  }

  protected createRule(): void {
    if (this.ruleForm.invalid) {
      return;
    }
    this.store.createRule(this.ruleForm.getRawValue(), () =>
      this.ruleForm.reset({ leftPermissionCode: '', rightPermissionCode: '', reason: '' }),
    );
  }
}
