import { ChangeDetectionStrategy, Component, OnInit, inject } from '@angular/core';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { EmptyStateComponent } from '../../../../shared/ui/empty-state.component';
import { LoadingIndicatorComponent } from '../../../../shared/ui/loading-indicator.component';
import { PageHeaderComponent } from '../../../../shared/ui/page-header.component';
import { OrdersStore } from '../../data-access/orders.store';

@Component({
  selector: 'app-orders-page',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [ReactiveFormsModule, PageHeaderComponent, EmptyStateComponent, LoadingIndicatorComponent],
  providers: [OrdersStore],
  templateUrl: './orders-page.component.html',
})
export class OrdersPageComponent implements OnInit {
  protected readonly store = inject(OrdersStore);
  private readonly fb = inject(FormBuilder);

  protected readonly form = this.fb.nonNullable.group({
    recipeId: ['', Validators.required],
    volumeLiters: [0, [Validators.required, Validators.min(0.001)]],
  });

  ngOnInit(): void {
    this.store.load();
  }

  protected create(): void {
    if (this.form.invalid) {
      return;
    }
    this.store.create(this.form.getRawValue(), () => this.form.reset({ volumeLiters: 0 }));
  }

  protected badgeClass(status: string): string {
    return status === 'DRAFT' ? 'bg-secondary-subtle text-secondary-emphasis'
      : status === 'CANCELLED' ? 'bg-danger-subtle text-danger-emphasis'
      : 'bg-info-subtle text-info-emphasis';
  }
}
