import { ChangeDetectionStrategy, Component, OnInit, inject, signal } from '@angular/core';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { ActivatedRoute, RouterLink } from '@angular/router';
import { LoadingIndicatorComponent } from '../../../../shared/ui/loading-indicator.component';
import { PageHeaderComponent } from '../../../../shared/ui/page-header.component';
import { CycleDetailStore } from '../../data-access/cycle-detail.store';
import { CycleStep } from '../../domain/cycle.model';

@Component({
  selector: 'app-cycle-detail-page',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [ReactiveFormsModule, RouterLink, PageHeaderComponent, LoadingIndicatorComponent],
  providers: [CycleDetailStore],
  templateUrl: './cycle-detail-page.component.html',
})
export class CycleDetailPageComponent implements OnInit {
  protected readonly store = inject(CycleDetailStore);
  private readonly fb = inject(FormBuilder);
  private readonly route = inject(ActivatedRoute);

  /** Sequência da etapa que está sendo registrada (painel inline). */
  protected readonly recording = signal<number | null>(null);

  protected readonly form = this.fb.nonNullable.group({
    measuredConcentrationPct: this.fb.control<number | null>(null),
    measuredTempC: this.fb.control<number | null>(null),
    measuredTimeMinutes: this.fb.control<number | null>(null),
    flow: [''],
    evidence: [''],
    outOfOrderReason: [''],
    override: [false],
    overrideReason: [''],
  });

  protected readonly interruptForm = this.fb.nonNullable.group({
    reason: ['', Validators.required],
  });

  protected readonly verifyForm = this.fb.nonNullable.group({
    rinseOk: [false],
    visualOk: [false],
    atpRlu: this.fb.control<number | null>(null, Validators.required),
    atpThreshold: this.fb.control<number | null>(null, Validators.required),
    microOk: [false],
  });

  protected readonly consumptionForm = this.fb.nonNullable.group({
    waterLiters: this.fb.control<number | null>(null, [Validators.required, Validators.min(0)]),
    energyKwh: this.fb.control<number | null>(null, [Validators.required, Validators.min(0)]),
    productKg: this.fb.control<number | null>(null, [Validators.required, Validators.min(0)]),
  });

  ngOnInit(): void {
    this.store.load(this.route.snapshot.paramMap.get('id')!);
  }

  protected startRecording(step: CycleStep): void {
    this.recording.set(step.sequence);
    this.form.reset({ measuredConcentrationPct: null, measuredTempC: null, measuredTimeMinutes: null,
      flow: '', evidence: '', outOfOrderReason: '', override: false, overrideReason: '' });
  }

  protected submitStep(): void {
    const sequence = this.recording();
    if (sequence === null) {
      return;
    }
    const v = this.form.getRawValue();
    this.store.recordStep({
      sequence,
      measuredConcentrationPct: v.measuredConcentrationPct,
      measuredTempC: v.measuredTempC,
      measuredTimeMinutes: v.measuredTimeMinutes,
      flow: v.flow || null,
      evidence: v.evidence || null,
      outOfOrderReason: v.outOfOrderReason || null,
      override: v.override,
      overrideReason: v.overrideReason || null,
    }, () => this.recording.set(null));
  }

  protected interrupt(): void {
    if (this.interruptForm.invalid) {
      return;
    }
    this.store.interrupt(this.interruptForm.getRawValue().reason);
    this.interruptForm.reset({ reason: '' });
  }

  protected verify(): void {
    if (this.verifyForm.invalid) {
      return;
    }
    const v = this.verifyForm.getRawValue();
    this.store.verify({
      rinseOk: v.rinseOk,
      visualOk: v.visualOk,
      atpRlu: v.atpRlu!,
      atpThreshold: v.atpThreshold!,
      microOk: v.microOk,
    });
  }

  protected recordConsumption(): void {
    if (this.consumptionForm.invalid) {
      return;
    }
    const v = this.consumptionForm.getRawValue();
    this.store.recordConsumption({ waterLiters: v.waterLiters!, energyKwh: v.energyKwh!, productKg: v.productKg! });
  }

  protected compare(procedureCode: string): void {
    this.store.loadSummary(procedureCode);
  }
}
