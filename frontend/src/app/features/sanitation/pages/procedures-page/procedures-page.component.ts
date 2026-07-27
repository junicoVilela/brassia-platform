import { ChangeDetectionStrategy, Component, OnInit, inject, signal } from '@angular/core';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { AuthService } from '../../../../core/auth/auth.service';
import { EmptyStateComponent } from '../../../../shared/ui/empty-state.component';
import { LoadingIndicatorComponent } from '../../../../shared/ui/loading-indicator.component';
import { PageHeaderComponent } from '../../../../shared/ui/page-header.component';
import { ProceduresStore } from '../../data-access/procedures.store';
import { ProcedureStep } from '../../domain/procedure.model';

@Component({
  selector: 'app-procedures-page',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [ReactiveFormsModule, PageHeaderComponent, EmptyStateComponent, LoadingIndicatorComponent],
  providers: [ProceduresStore],
  templateUrl: './procedures-page.component.html',
})
export class ProceduresPageComponent implements OnInit {
  protected readonly store = inject(ProceduresStore);
  private readonly fb = inject(FormBuilder);
  private readonly auth = inject(AuthService);

  protected readonly canManage = this.auth.hasPermission('sanitation.procedure.manage');

  /** Etapas montadas antes de submeter o POP. */
  protected readonly steps = signal<ProcedureStep[]>([]);

  protected readonly form = this.fb.nonNullable.group({
    code: ['', [Validators.required, Validators.maxLength(40)]],
    name: ['', [Validators.required, Validators.maxLength(160)]],
  });

  protected readonly stepForm = this.fb.nonNullable.group({
    method: ['', Validators.required],
    product: [''],
    concentrationMinPct: this.fb.control<number | null>(null),
    concentrationMaxPct: this.fb.control<number | null>(null),
    tempMinC: this.fb.control<number | null>(null),
    tempMaxC: this.fb.control<number | null>(null),
    timeMinutes: this.fb.control<number | null>(null),
    flow: [''],
    ppe: [''],
    alternative: [''],
    prohibition: [''],
    evidenceRequired: [false],
  });

  ngOnInit(): void {
    this.store.load();
  }

  protected addStep(): void {
    if (this.stepForm.invalid) {
      return;
    }
    const v = this.stepForm.getRawValue();
    this.steps.update(list => [...list, {
      sequence: list.length + 1,
      method: v.method,
      product: v.product || null,
      concentrationMinPct: v.concentrationMinPct,
      concentrationMaxPct: v.concentrationMaxPct,
      tempMinC: v.tempMinC,
      tempMaxC: v.tempMaxC,
      timeMinutes: v.timeMinutes,
      flow: v.flow || null,
      ppe: v.ppe || null,
      alternative: v.alternative || null,
      prohibition: v.prohibition || null,
      evidenceRequired: v.evidenceRequired,
    }]);
    this.stepForm.reset({ method: '', product: '', concentrationMinPct: null, concentrationMaxPct: null,
      tempMinC: null, tempMaxC: null, timeMinutes: null, flow: '', ppe: '', alternative: '', prohibition: '',
      evidenceRequired: false });
  }

  protected removeStep(index: number): void {
    this.steps.update(list => list.filter((_, i) => i !== index).map((s, i) => ({ ...s, sequence: i + 1 })));
  }

  protected create(): void {
    if (this.form.invalid || this.steps().length === 0) {
      return;
    }
    const v = this.form.getRawValue();
    this.store.create({ code: v.code, name: v.name, steps: this.steps() }, () => {
      this.form.reset({ code: '', name: '' });
      this.steps.set([]);
    });
  }
}
