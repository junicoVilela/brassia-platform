import { DatePipe, DecimalPipe } from '@angular/common';
import { ChangeDetectionStrategy, Component, OnInit, inject } from '@angular/core';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { AuthService } from '../../../../core/auth/auth.service';
import { EmptyStateComponent } from '../../../../shared/ui/empty-state.component';
import { LoadingIndicatorComponent } from '../../../../shared/ui/loading-indicator.component';
import { PageHeaderComponent } from '../../../../shared/ui/page-header.component';
import { QualityStore } from '../../data-access/quality.store';
import {
  CAPA_KIND_LABELS,
  CapaActionKindCode,
  ControlPlan,
  ControlPoint,
  FREQUENCY_LABELS,
  FrequencyKindCode,
  ProcessStageCode,
  NC_SOURCE_LABELS,
  NcSourceCode,
  NonConformity,
  SEVERITY_LABELS,
  STAGE_LABELS,
  SeverityCode,
} from '../../domain/quality.model';

@Component({
  selector: 'app-control-plans-page',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [
    ReactiveFormsModule,
    DatePipe,
    DecimalPipe,
    PageHeaderComponent,
    EmptyStateComponent,
    LoadingIndicatorComponent,
  ],
  providers: [QualityStore],
  templateUrl: './control-plans-page.component.html',
})
export class ControlPlansPageComponent implements OnInit {
  protected readonly store = inject(QualityStore);
  private readonly auth = inject(AuthService);
  private readonly fb = inject(FormBuilder);

  protected readonly canManage = this.auth.hasPermission('quality.plan.manage');
  protected readonly canMeasure = this.auth.hasPermission('quality.measurement.record');

  protected readonly stageLabels = STAGE_LABELS;
  protected readonly severityLabels = SEVERITY_LABELS;
  protected readonly frequencyLabels = FREQUENCY_LABELS;
  protected readonly stageOptions = Object.keys(STAGE_LABELS) as ProcessStageCode[];
  protected readonly severityOptions = Object.keys(SEVERITY_LABELS) as SeverityCode[];
  protected readonly frequencyOptions = Object.keys(FREQUENCY_LABELS) as FrequencyKindCode[];

  protected readonly form = this.fb.nonNullable.group({
    code: ['', Validators.required],
    name: ['', Validators.required],
    stage: ['BREWING' as ProcessStageCode, Validators.required],
  });

  protected readonly pointForm = this.fb.nonNullable.group({
    parameter: ['', Validators.required],
    min: [null as number | null],
    max: [null as number | null],
    target: [null as number | null],
    unit: ['', Validators.required],
    frequencyKind: ['PER_BATCH' as FrequencyKindCode, Validators.required],
    everyHours: [null as number | null],
    action: ['', Validators.required],
    severity: ['MAJOR' as SeverityCode, Validators.required],
    critical: [false],
  });

  protected readonly canManageNc = this.auth.hasPermission('quality.nc.manage');
  protected readonly canCloseNc = this.auth.hasPermission('quality.nc.close');

  protected readonly sourceLabels = NC_SOURCE_LABELS;
  protected readonly capaKindLabels = CAPA_KIND_LABELS;
  protected readonly sourceOptions = Object.keys(NC_SOURCE_LABELS) as NcSourceCode[];
  protected readonly capaKindOptions = Object.keys(CAPA_KIND_LABELS) as CapaActionKindCode[];

  protected readonly ncForm = this.fb.nonNullable.group({
    code: ['', Validators.required],
    title: ['', Validators.required],
    description: ['', Validators.required],
    source: ['OTHER' as NcSourceCode, Validators.required],
    severity: ['MAJOR' as SeverityCode, Validators.required],
    containmentDueOn: ['', Validators.required],
    investigationDueOn: ['', Validators.required],
    verificationDueOn: ['', Validators.required],
  });

  protected readonly containForm = this.fb.nonNullable.group({ description: ['', Validators.required] });

  protected readonly investigateForm = this.fb.nonNullable.group({
    rootCause: ['', Validators.required],
    method: ['', Validators.required],
  });

  protected readonly capaForm = this.fb.nonNullable.group({
    kind: ['CORRECTIVE' as CapaActionKindCode, Validators.required],
    description: ['', Validators.required],
    owner: ['', Validators.required],
    dueOn: ['', Validators.required],
  });

  protected readonly verifyForm = this.fb.nonNullable.group({
    effective: [true],
    evidence: ['', Validators.required],
  });

  protected readonly measurementForm = this.fb.nonNullable.group({
    pointId: ['', Validators.required],
    value: [0, Validators.required],
    instrumentId: [''],
    note: [''],
  });

  ngOnInit(): void {
    this.store.load();
  }

  protected create(): void {
    if (this.form.invalid) {
      return;
    }
    this.store.create({ ...this.form.getRawValue(), recipeId: null }, () => this.form.reset());
  }

  protected addPoint(plan: ControlPlan): void {
    if (this.pointForm.invalid) {
      return;
    }
    this.store.addPoint(plan.id, this.pointForm.getRawValue(), () =>
      this.pointForm.reset({ frequencyKind: 'PER_BATCH', severity: 'MAJOR', critical: false }),
    );
  }

  protected measure(plan: ControlPlan): void {
    if (this.measurementForm.invalid) {
      return;
    }
    const value = this.measurementForm.getRawValue();
    this.store.measure(
      {
        planId: plan.id,
        pointId: value.pointId,
        batchId: null,
        instrumentId: value.instrumentId || null,
        value: value.value,
        note: value.note || null,
        measuredAt: null,
      },
      () => this.measurementForm.reset({ value: 0 }),
    );
  }

  protected removePoint(plan: ControlPlan, point: ControlPoint): void {
    this.store.removePoint(plan.id, point.id);
  }

  protected openNc(): void {
    if (this.ncForm.invalid) {
      return;
    }
    this.store.openNonConformity({ ...this.ncForm.getRawValue(), deviationId: null }, () =>
      this.ncForm.reset({ source: 'OTHER', severity: 'MAJOR' }),
    );
  }

  protected contain(nc: NonConformity): void {
    if (this.containForm.invalid) {
      return;
    }
    this.store.contain(nc.id, this.containForm.getRawValue().description);
    this.containForm.reset();
  }

  protected investigate(nc: NonConformity): void {
    if (this.investigateForm.invalid) {
      return;
    }
    const value = this.investigateForm.getRawValue();
    this.store.investigate(nc.id, value.rootCause, value.method);
    this.investigateForm.reset();
  }

  protected planCapa(nc: NonConformity): void {
    if (this.capaForm.invalid) {
      return;
    }
    this.store.planAction(nc.id, this.capaForm.getRawValue(), () =>
      this.capaForm.reset({ kind: 'CORRECTIVE' }),
    );
  }

  protected verify(nc: NonConformity): void {
    if (this.verifyForm.invalid) {
      return;
    }
    const value = this.verifyForm.getRawValue();
    this.store.verify(nc.id, value.effective, value.evidence);
    this.verifyForm.reset({ effective: true });
  }

  /** Badge da severidade: crítica e grave pedem destaque; leve é informativa. */
  protected severityClass(severity: SeverityCode): string {
    switch (severity) {
      case 'CRITICAL':
        return 'text-bg-danger';
      case 'MAJOR':
        return 'text-bg-warning';
      default:
        return 'text-bg-secondary';
    }
  }
}
