import { DatePipe, DecimalPipe } from '@angular/common';
import { ChangeDetectionStrategy, Component, OnInit, inject } from '@angular/core';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { AuthService } from '../../../../core/auth/auth.service';
import { EmptyStateComponent } from '../../../../shared/ui/empty-state.component';
import { LoadingIndicatorComponent } from '../../../../shared/ui/loading-indicator.component';
import { PageHeaderComponent } from '../../../../shared/ui/page-header.component';
import { QualityStore } from '../../data-access/quality.store';
import {
  ControlPlan,
  ControlPoint,
  FREQUENCY_LABELS,
  FrequencyKindCode,
  ProcessStageCode,
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
