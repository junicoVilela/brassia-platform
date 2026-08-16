import { DatePipe, DecimalPipe } from '@angular/common';
import { ChangeDetectionStrategy, Component, OnInit, inject, signal } from '@angular/core';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { AuthService } from '../../../../core/auth/auth.service';
import { EmptyStateComponent } from '../../../../shared/ui/empty-state.component';
import { LoadingIndicatorComponent } from '../../../../shared/ui/loading-indicator.component';
import { PageHeaderComponent } from '../../../../shared/ui/page-header.component';
import { ContainersStore } from '../../data-access/containers.store';
import {
  CONDITION_LABELS,
  Container,
  ContainerIdentifier,
  ContainerState,
  OWNERSHIP_LABELS,
  STATE_HELP,
  STATE_LABELS,
} from '../../domain/container.model';

/**
 * Contêineres retornáveis (CON-001).
 *
 * <p>A responsabilidade da tela que não é listar: <strong>deixar claro que "voltou" não é "pronto"</strong>.
 * O keg que chegou do cliente aparece numa fila de trabalho, e não no estoque disponível — porque a
 * confusão entre os dois enche um vasilhame que ninguém lavou.
 */
@Component({
  selector: 'app-containers-page',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [
    DatePipe,
    DecimalPipe,
    ReactiveFormsModule,
    PageHeaderComponent,
    LoadingIndicatorComponent,
    EmptyStateComponent,
  ],
  providers: [ContainersStore],
  templateUrl: './containers-page.component.html',
})
export class ContainersPageComponent implements OnInit {
  protected readonly store = inject(ContainersStore);
  private readonly auth = inject(AuthService);
  private readonly fb = inject(FormBuilder);

  protected readonly stateLabels = STATE_LABELS;
  protected readonly stateHelp = STATE_HELP;
  protected readonly conditionLabels = CONDITION_LABELS;
  protected readonly ownershipLabels = OWNERSHIP_LABELS;

  protected readonly states: ContainerState[] = [
    'EMPTY',
    'FILLED',
    'IN_TRANSIT',
    'AT_CUSTOMER',
    'RETURNED',
    'IN_MAINTENANCE',
    'RETIRED',
  ];

  protected readonly canManage = this.auth.hasPermission('container.manage');
  protected readonly canInspect = this.auth.hasPermission('container.inspect');

  protected readonly showForm = signal(false);

  protected readonly form = this.fb.nonNullable.group({
    code: ['', [Validators.required, Validators.maxLength(40)]],
    kind: ['KEG' as const, Validators.required],
    nominalCapacityLiters: [50, [Validators.required, Validators.min(0.001)]],
    ownership: ['OWN' as const, Validators.required],
  });

  protected readonly scanForm = this.fb.nonNullable.group({
    value: ['', Validators.required],
  });

  protected readonly labelForm = this.fb.nonNullable.group({
    value: ['', [Validators.required, Validators.maxLength(120)]],
    technology: ['QR' as const, Validators.required],
  });

  protected readonly inspectionForm = this.fb.nonNullable.group({
    validUntil: ['', Validators.required],
    note: ['', Validators.maxLength(500)],
  });

  protected readonly inspecting = signal<Container | null>(null);
  protected readonly retiring = signal<Container | null>(null);

  protected readonly retireForm = this.fb.nonNullable.group({
    reason: ['', [Validators.required, Validators.maxLength(500)]],
  });

  ngOnInit(): void {
    this.store.load();
  }

  protected submit(): void {
    if (this.form.invalid) {
      this.form.markAllAsTouched();
      return;
    }
    const v = this.form.getRawValue();
    this.store.register(v.code, v.kind, v.nominalCapacityLiters, v.ownership);
    this.form.reset({ code: '', kind: 'KEG', nominalCapacityLiters: 50, ownership: 'OWN' });
    this.showForm.set(false);
  }

  protected scan(): void {
    if (this.scanForm.invalid) {
      return;
    }
    this.store.scan(this.scanForm.getRawValue().value);
  }

  protected move(container: Container, to: ContainerState): void {
    this.store.move(container, to);
  }

  protected openInspection(container: Container): void {
    this.inspectionForm.reset({ validUntil: '', note: '' });
    this.inspecting.set(container);
  }

  protected submitInspection(): void {
    const container = this.inspecting();
    if (!container || this.inspectionForm.invalid) {
      this.inspectionForm.markAllAsTouched();
      return;
    }
    const v = this.inspectionForm.getRawValue();
    this.store.inspect(container, v.validUntil, v.note || null);
    this.inspecting.set(null);
  }

  protected openLabels(container: Container): void {
    this.labelForm.reset({ value: '', technology: 'QR' });
    this.store.openIdentifiers(container);
  }

  protected submitLabel(): void {
    if (this.labelForm.invalid) {
      this.labelForm.markAllAsTouched();
      return;
    }
    const v = this.labelForm.getRawValue();
    this.store.assign(v.value, v.technology);
    this.labelForm.reset({ value: '', technology: 'QR' });
  }

  protected retireLabel(identifier: ContainerIdentifier): void {
    this.store.retireIdentifier(identifier);
  }

  protected openRetire(container: Container): void {
    this.retireForm.reset({ reason: '' });
    this.retiring.set(container);
  }

  protected submitRetire(): void {
    const container = this.retiring();
    if (!container || this.retireForm.invalid) {
      this.retireForm.markAllAsTouched();
      return;
    }
    this.store.retire(container, this.retireForm.getRawValue().reason);
    this.retiring.set(null);
  }

  protected filterBy(value: string): void {
    this.store.filterBy(value ? (value as ContainerState) : null);
  }
}
