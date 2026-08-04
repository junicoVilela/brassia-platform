import { DatePipe, DecimalPipe } from '@angular/common';
import { ChangeDetectionStrategy, Component, OnInit, inject } from '@angular/core';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { AuthService } from '../../../../core/auth/auth.service';
import { EmptyStateComponent } from '../../../../shared/ui/empty-state.component';
import { LoadingIndicatorComponent } from '../../../../shared/ui/loading-indicator.component';
import { PageHeaderComponent } from '../../../../shared/ui/page-header.component';
import { SensoryStore } from '../../data-access/sensory.store';
import {
  ATTRIBUTE_LABELS,
  ATTRIBUTE_ORDER,
  AttributeCode,
  SensorySample,
  SensorySession,
} from '../../domain/sensory.model';

@Component({
  selector: 'app-sensory-sessions-page',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [
    ReactiveFormsModule,
    DatePipe,
    DecimalPipe,
    PageHeaderComponent,
    EmptyStateComponent,
    LoadingIndicatorComponent,
  ],
  providers: [SensoryStore],
  templateUrl: './sessions-page.component.html',
})
export class SessionsPageComponent implements OnInit {
  protected readonly store = inject(SensoryStore);
  private readonly auth = inject(AuthService);
  private readonly fb = inject(FormBuilder);

  protected readonly canManage = this.auth.hasPermission('sensory.session.manage');
  protected readonly canEvaluate = this.auth.hasPermission('sensory.evaluation.submit');

  protected readonly attributeLabels = ATTRIBUTE_LABELS;
  protected readonly attributes = ATTRIBUTE_ORDER;

  protected readonly form = this.fb.nonNullable.group({
    code: ['', Validators.required],
    purpose: ['', Validators.required],
    scheduledFor: ['', Validators.required],
  });

  protected readonly sampleForm = this.fb.nonNullable.group({
    batchId: ['', Validators.required],
    note: [''],
  });

  /** A ficha nasce com todos os atributos: incompleta, o backend recusa. */
  protected readonly evaluationForm = this.fb.nonNullable.group({
    sampleId: ['', Validators.required],
    APPEARANCE: [5, Validators.required],
    AROMA: [5, Validators.required],
    FLAVOR: [5, Validators.required],
    BODY: [5, Validators.required],
    OVERALL: [5, Validators.required],
    descriptors: [''],
    note: [''],
  });

  ngOnInit(): void {
    this.store.load();
  }

  protected create(): void {
    if (this.form.invalid) {
      return;
    }
    this.store.create(this.form.getRawValue(), () => this.form.reset());
  }

  protected addSample(session: SensorySession): void {
    if (this.sampleForm.invalid) {
      return;
    }
    const value = this.sampleForm.getRawValue();
    this.store.addSample(session.id, { batchId: value.batchId, note: value.note || null }, () =>
      this.sampleForm.reset(),
    );
  }

  protected submit(session: SensorySession): void {
    if (this.evaluationForm.invalid) {
      return;
    }
    const value = this.evaluationForm.getRawValue();
    const scores: Record<string, number> = {};
    for (const attribute of this.attributes) {
      scores[attribute] = value[attribute];
    }
    this.store.submit(
      session.id,
      {
        sampleId: value.sampleId,
        scores,
        descriptors: this.parseDescriptors(value.descriptors),
        note: value.note || null,
      },
      () => this.evaluationForm.reset({ APPEARANCE: 5, AROMA: 5, FLAVOR: 5, BODY: 5, OVERALL: 5 }),
    );
  }

  /** Descritores separados por vírgula; a biblioteca estruturada é a SEN-002. */
  private parseDescriptors(raw: string): string[] {
    return raw
      .split(',')
      .map(d => d.trim())
      .filter(d => d.length > 0);
  }

  protected removeSample(session: SensorySession, sample: SensorySample): void {
    this.store.removeSample(session.id, sample.id);
  }

  protected attributeControl(attribute: AttributeCode) {
    return this.evaluationForm.controls[attribute];
  }

  protected statusClass(session: SensorySession): string {
    switch (session.status) {
      case 'OPEN':
        return 'text-bg-success';
      case 'CLOSED':
        return 'text-bg-secondary';
      default:
        return 'text-bg-info';
    }
  }
}
