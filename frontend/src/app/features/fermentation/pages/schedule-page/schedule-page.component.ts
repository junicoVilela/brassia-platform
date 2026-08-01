import { DatePipe } from '@angular/common';
import { ChangeDetectionStrategy, Component, OnInit, inject } from '@angular/core';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { AuthService } from '../../../../core/auth/auth.service';
import { EmptyStateComponent } from '../../../../shared/ui/empty-state.component';
import { LoadingIndicatorComponent } from '../../../../shared/ui/loading-indicator.component';
import { PageHeaderComponent } from '../../../../shared/ui/page-header.component';
import { ScheduleStore } from '../../data-access/schedule.store';
import {
  SCHEDULE_ACTIONS,
  SCHEDULE_ACTION_LABELS,
  ScheduleAction,
} from '../../domain/schedule.model';

@Component({
  selector: 'app-schedule-page',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [
    DatePipe,
    ReactiveFormsModule,
    PageHeaderComponent,
    EmptyStateComponent,
    LoadingIndicatorComponent,
  ],
  providers: [ScheduleStore],
  templateUrl: './schedule-page.component.html',
})
export class SchedulePageComponent implements OnInit {
  protected readonly store = inject(ScheduleStore);
  private readonly fb = inject(FormBuilder);
  private readonly auth = inject(AuthService);

  protected readonly canManage = this.auth.hasPermission('fermentation.schedule.manage');
  protected readonly actions = SCHEDULE_ACTIONS;
  protected readonly actionLabels = SCHEDULE_ACTION_LABELS;

  protected readonly planForm = this.fb.nonNullable.group({
    profileId: ['', Validators.required],
    start: ['', Validators.required],
    responsibleUserId: ['', Validators.required],
    defaultDurationDays: this.fb.control<number | null>(3, Validators.min(1)),
    toleranceHours: this.fb.control<number | null>(12, Validators.min(0)),
  });

  protected readonly stepForm = this.fb.nonNullable.group({
    name: ['', Validators.required],
    action: this.fb.nonNullable.control<ScheduleAction>('DRY_HOP', Validators.required),
    plannedStart: ['', Validators.required],
    plannedEnd: ['', Validators.required],
    toleranceHours: this.fb.nonNullable.control(6, [Validators.required, Validators.min(0)]),
    responsibleUserId: ['', Validators.required],
    dependsOnPrevious: [true],
  });

  ngOnInit(): void {
    this.store.loadBatches();
    this.store.loadProfiles();
  }

  protected plan(): void {
    if (this.planForm.invalid) {
      return;
    }
    const v = this.planForm.getRawValue();
    this.store.plan(
      {
        profileId: v.profileId,
        start: new Date(v.start).toISOString(),
        responsibleUserId: v.responsibleUserId,
        defaultDurationDays: v.defaultDurationDays,
        toleranceHours: v.toleranceHours,
      },
      () => this.planForm.patchValue({ start: '' }),
    );
  }

  protected addStep(): void {
    if (this.stepForm.invalid) {
      return;
    }
    const v = this.stepForm.getRawValue();
    this.store.addStep(
      {
        name: v.name,
        action: v.action,
        // Etapas específicas do lote avançam por decisão humana nesta fatia.
        condition: 'MANUAL',
        conditionDays: null,
        targetGravity: null,
        plannedStart: new Date(v.plannedStart).toISOString(),
        plannedEnd: new Date(v.plannedEnd).toISOString(),
        toleranceHours: v.toleranceHours,
        responsibleUserId: v.responsibleUserId,
        dependsOnPrevious: v.dependsOnPrevious,
      },
      () => this.stepForm.patchValue({ name: '', plannedStart: '', plannedEnd: '' }),
    );
  }

  /** Pede a prévia; nada é gravado até o cervejeiro confirmar. */
  protected askPreview(stepId: string, currentStart: string): void {
    const answer = window.prompt('Novo início da etapa (AAAA-MM-DDTHH:mm):', currentStart.slice(0, 16));
    if (answer) {
      this.store.previewReschedule(stepId, new Date(answer).toISOString());
    }
  }

  protected registerExecution(stepId: string): void {
    const at = window.prompt('Instante da execução (AAAA-MM-DDTHH:mm):',
      new Date().toISOString().slice(0, 16));
    if (!at) {
      return;
    }
    // Fora da tolerância o backend exige justificativa; pedimos sempre, aceitando vazio.
    const justification = window.prompt('Justificativa (obrigatória fora da tolerância):') ?? '';
    this.store.execute(stepId, new Date(at).toISOString(), justification.trim() || null);
  }
}
