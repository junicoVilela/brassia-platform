import { ChangeDetectionStrategy, Component, OnInit, inject } from '@angular/core';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { EmptyStateComponent } from '../../../../../shared/ui/empty-state.component';
import { LoadingIndicatorComponent } from '../../../../../shared/ui/loading-indicator.component';
import { PageHeaderComponent } from '../../../../../shared/ui/page-header.component';
import { ServiceAccountsStore } from '../../data-access/service-accounts.store';
import { ServiceAccount } from '../../domain/service-account.model';

@Component({
  selector: 'app-service-accounts-page',
  standalone: true,
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [ReactiveFormsModule, PageHeaderComponent, EmptyStateComponent, LoadingIndicatorComponent],
  providers: [ServiceAccountsStore],
  templateUrl: './service-accounts-page.component.html',
})
export class ServiceAccountsPageComponent implements OnInit {
  protected readonly store = inject(ServiceAccountsStore);
  private readonly fb = inject(FormBuilder);

  protected readonly form = this.fb.nonNullable.group({
    code: ['', [Validators.required, Validators.maxLength(80)]],
    name: ['', [Validators.required, Validators.maxLength(160)]],
  });

  /** Escopos digitados por conta (id → texto separado por vírgula). */
  protected readonly scopeInputs = new Map<string, string>();

  ngOnInit(): void {
    this.store.load();
  }

  protected create(): void {
    if (this.form.invalid) {
      return;
    }
    this.store.create(this.form.getRawValue(), () => this.form.reset({ code: '', name: '' }));
  }

  protected issue(account: ServiceAccount): void {
    const raw = (this.scopeInputs.get(account.id) ?? '').trim();
    const scopes = raw ? raw.split(',').map(s => s.trim()).filter(s => s) : [];
    if (scopes.length === 0) {
      return;
    }
    this.store.issueCredential(account, scopes);
    this.scopeInputs.set(account.id, '');
  }

  protected onScopeInput(accountId: string, value: string): void {
    this.scopeInputs.set(accountId, value);
  }

  protected toggleCredentials(account: ServiceAccount): void {
    const open = this.store.selected()?.id === account.id;
    this.store.selectAccount(open ? null : account);
  }
}
