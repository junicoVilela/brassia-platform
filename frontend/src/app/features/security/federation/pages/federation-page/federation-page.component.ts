import { ChangeDetectionStrategy, Component, OnInit, inject, signal } from '@angular/core';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { EmptyStateComponent } from '../../../../../shared/ui/empty-state.component';
import { LoadingIndicatorComponent } from '../../../../../shared/ui/loading-indicator.component';
import { PageHeaderComponent } from '../../../../../shared/ui/page-header.component';
import { DatePipe } from '@angular/common';
import { FederationStore } from '../../data-access/federation.store';
import { FederationProtocol, FederationProvider } from '../../domain/federation.model';

@Component({
  selector: 'app-federation-page',
  standalone: true,
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [ReactiveFormsModule, DatePipe, PageHeaderComponent, EmptyStateComponent, LoadingIndicatorComponent],
  providers: [FederationStore],
  templateUrl: './federation-page.component.html',
})
export class FederationPageComponent implements OnInit {
  protected readonly store = inject(FederationStore);
  private readonly fb = inject(FormBuilder);

  protected readonly protocols: FederationProtocol[] = ['SAML', 'OIDC'];
  protected readonly configError = signal<string | null>(null);

  protected readonly form = this.fb.nonNullable.group({
    code: ['', [Validators.required, Validators.maxLength(80)]],
    displayName: ['', [Validators.required, Validators.maxLength(160)]],
    protocol: this.fb.nonNullable.control<FederationProtocol>('SAML', Validators.required),
    issuerOrEntityId: ['', Validators.required],
    configuration: [''],
  });

  ngOnInit(): void {
    this.store.load();
  }

  protected create(): void {
    if (this.form.invalid) {
      return;
    }
    const raw = this.form.getRawValue();
    let configuration: Record<string, unknown> = {};
    const text = raw.configuration.trim();
    if (text) {
      try {
        configuration = JSON.parse(text) as Record<string, unknown>;
      } catch {
        this.configError.set('Configuração inválida: informe um JSON válido.');
        return;
      }
    }
    this.configError.set(null);
    this.store.create(
      {
        code: raw.code,
        displayName: raw.displayName,
        protocol: raw.protocol,
        issuerOrEntityId: raw.issuerOrEntityId,
        configuration,
      },
      () => this.form.reset({ code: '', displayName: '', protocol: 'SAML', issuerOrEntityId: '', configuration: '' }),
    );
  }

  protected toggleIdentities(provider: FederationProvider): void {
    const open = this.store.selected()?.id === provider.id;
    this.store.selectProvider(open ? null : provider);
  }

  protected statusClass(status: string): string {
    switch (status) {
      case 'VALIDATED': return 'bg-success-subtle text-success-emphasis';
      case 'INVALID': return 'bg-danger-subtle text-danger-emphasis';
      default: return 'bg-secondary-subtle text-secondary-emphasis';
    }
  }
}
