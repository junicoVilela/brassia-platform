import { ChangeDetectionStrategy, Component, OnInit, computed, inject } from '@angular/core';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { UiSearchService } from '../../../../../core/search/ui-search.service';
import { EmptyStateComponent } from '../../../../../shared/ui/empty-state.component';
import { LoadingIndicatorComponent } from '../../../../../shared/ui/loading-indicator.component';
import { PageHeaderComponent } from '../../../../../shared/ui/page-header.component';
import { AccountStatus } from '../../domain/user.model';
import { UsersStore } from '../../data-access/users.store';

@Component({
  selector: 'app-users-page',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [ReactiveFormsModule, PageHeaderComponent, EmptyStateComponent, LoadingIndicatorComponent],
  providers: [UsersStore],
  templateUrl: './users-page.component.html',
})
export class UsersPageComponent implements OnInit {
  protected readonly store = inject(UsersStore);
  protected readonly search = inject(UiSearchService);
  private readonly fb = inject(FormBuilder);

  protected readonly filtered = computed(() => {
    const term = this.search.term().trim().toLowerCase();
    const items = this.store.items();
    if (!term) {
      return items;
    }
    return items.filter(u => `${u.displayName} ${u.email} ${u.status}`.toLowerCase().includes(term));
  });

  protected readonly form = this.fb.nonNullable.group({
    email: ['', [Validators.required, Validators.email]],
    displayName: ['', [Validators.required, Validators.maxLength(160)]],
  });

  ngOnInit(): void {
    this.store.load();
  }

  protected invite(): void {
    if (this.form.invalid) {
      return;
    }
    this.store.invite(this.form.getRawValue(), () => this.form.reset());
  }

  protected badgeClass(status: AccountStatus): string {
    switch (status) {
      case 'ACTIVE': return 'text-bg-success';
      case 'INVITED': return 'text-bg-info';
      case 'LOCKED': return 'text-bg-warning';
      case 'DISABLED': return 'text-bg-secondary';
    }
  }
}
