import { ChangeDetectionStrategy, Component, OnInit, inject, signal } from '@angular/core';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { AuthService } from '../../../../core/auth/auth.service';
import { EmptyStateComponent } from '../../../../shared/ui/empty-state.component';
import { LoadingIndicatorComponent } from '../../../../shared/ui/loading-indicator.component';
import { PageHeaderComponent } from '../../../../shared/ui/page-header.component';
import { ProfilesStore } from '../../data-access/profiles.store';
import { ADVANCE_CONDITIONS, AdvanceCondition, FermentationStage } from '../../domain/profile.model';

@Component({
  selector: 'app-profiles-page',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [ReactiveFormsModule, PageHeaderComponent, EmptyStateComponent, LoadingIndicatorComponent],
  providers: [ProfilesStore],
  templateUrl: './profiles-page.component.html',
})
export class ProfilesPageComponent implements OnInit {
  protected readonly store = inject(ProfilesStore);
  private readonly fb = inject(FormBuilder);
  private readonly auth = inject(AuthService);

  protected readonly canManage = this.auth.hasPermission('fermentation.profile.manage');
  protected readonly conditions = ADVANCE_CONDITIONS;

  /** Estágios montados antes de submeter o perfil. */
  protected readonly stages = signal<FermentationStage[]>([]);

  protected readonly form = this.fb.nonNullable.group({
    code: ['', [Validators.required, Validators.maxLength(40)]],
    name: ['', [Validators.required, Validators.maxLength(160)]],
    // Critério de estabilidade de FG (FER-003); em branco = padrão do domínio.
    stabilityWindowHours: this.fb.control<number | null>(48, Validators.min(1)),
    stabilityMinReadings: this.fb.control<number | null>(3, Validators.min(2)),
    stabilityToleranceSg: this.fb.control<number | null>(0.002, Validators.min(0)),
  });

  protected readonly stageForm = this.fb.nonNullable.group({
    name: ['', Validators.required],
    targetTempC: this.fb.control<number | null>(null, Validators.required),
    rampHours: this.fb.control<number | null>(null),
    pressurePsi: this.fb.control<number | null>(null),
    condition: this.fb.nonNullable.control<AdvanceCondition>('TIME', Validators.required),
    conditionDays: this.fb.control<number | null>(null),
    targetGravity: this.fb.control<number | null>(null),
    requiresConfirmation: [true],
  });

  ngOnInit(): void {
    this.store.load();
  }

  protected addStage(): void {
    if (this.stageForm.invalid) {
      return;
    }
    const v = this.stageForm.getRawValue();
    this.stages.update(list => [...list, {
      sequence: list.length + 1,
      name: v.name,
      targetTempC: v.targetTempC!,
      rampHours: v.rampHours,
      pressurePsi: v.pressurePsi,
      condition: v.condition,
      conditionDays: v.condition === 'TIME' ? v.conditionDays : null,
      targetGravity: v.condition === 'GRAVITY' ? v.targetGravity : null,
      requiresConfirmation: v.requiresConfirmation,
    }]);
    this.stageForm.reset({ name: '', targetTempC: null, rampHours: null, pressurePsi: null, condition: 'TIME',
      conditionDays: null, targetGravity: null, requiresConfirmation: true });
  }

  protected removeStage(index: number): void {
    this.stages.update(list => list.filter((_, i) => i !== index).map((s, i) => ({ ...s, sequence: i + 1 })));
  }

  protected create(): void {
    if (this.form.invalid || this.stages().length === 0) {
      return;
    }
    const v = this.form.getRawValue();
    this.store.create(
      {
        code: v.code,
        name: v.name,
        stages: this.stages(),
        stability: v.stabilityWindowHours !== null && v.stabilityMinReadings !== null
          && v.stabilityToleranceSg !== null
          ? {
              windowHours: v.stabilityWindowHours,
              minReadings: v.stabilityMinReadings,
              toleranceSg: v.stabilityToleranceSg,
            }
          : null,
      },
      () => {
        this.form.reset({ code: '', name: '', stabilityWindowHours: 48, stabilityMinReadings: 3,
          stabilityToleranceSg: 0.002 });
        this.stages.set([]);
      },
    );
  }
}
