import { ChangeDetectionStrategy, Component, OnInit, inject } from '@angular/core';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { EmptyStateComponent } from '../../../../shared/ui/empty-state.component';
import { LoadingIndicatorComponent } from '../../../../shared/ui/loading-indicator.component';
import { PageHeaderComponent } from '../../../../shared/ui/page-header.component';
import { SuppliersStore } from '../../data-access/suppliers.store';

@Component({
  selector: 'app-suppliers-page',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [ReactiveFormsModule, PageHeaderComponent, EmptyStateComponent, LoadingIndicatorComponent],
  providers: [SuppliersStore],
  templateUrl: './suppliers-page.component.html',
})
export class SuppliersPageComponent implements OnInit {
  protected readonly store = inject(SuppliersStore);
  private readonly fb = inject(FormBuilder);

  protected readonly form = this.fb.nonNullable.group({
    name: ['', [Validators.required, Validators.maxLength(160)]],
    code: ['', [Validators.required, Validators.maxLength(40)]],
  });

  ngOnInit(): void {
    this.store.load();
  }

  protected register(): void {
    if (this.form.invalid) {
      return;
    }
    this.store.create(this.form.getRawValue(), () => this.form.reset({ name: '', code: '' }));
  }
}
