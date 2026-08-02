import { DatePipe, DecimalPipe } from '@angular/common';
import { ChangeDetectionStrategy, Component, OnInit, inject } from '@angular/core';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { AuthService } from '../../../../core/auth/auth.service';
import { EmptyStateComponent } from '../../../../shared/ui/empty-state.component';
import { LoadingIndicatorComponent } from '../../../../shared/ui/loading-indicator.component';
import { PageHeaderComponent } from '../../../../shared/ui/page-header.component';
import { PackagingStore } from '../../data-access/packaging.store';
import {
  CARBONATION_METHOD_LABELS,
  CHECKLIST_LABELS,
  CarbonationInput,
  CarbonationMethod,
  ChecklistItemCode,
  PACKAGING_STATUS_LABELS,
  PRIMING_SUGAR_LABELS,
  PackagingPlan,
  PrimingSugarCode,
} from '../../domain/packaging-plan.model';

@Component({
  selector: 'app-packaging-plans-page',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [
    DatePipe,
    DecimalPipe,
    ReactiveFormsModule,
    PageHeaderComponent,
    EmptyStateComponent,
    LoadingIndicatorComponent,
  ],
  providers: [PackagingStore],
  templateUrl: './plans-page.component.html',
})
export class PlansPageComponent implements OnInit {
  protected readonly store = inject(PackagingStore);
  private readonly fb = inject(FormBuilder);
  private readonly auth = inject(AuthService);

  protected readonly canManage = this.auth.hasPermission('packaging.plan.manage');
  protected readonly statusLabels = PACKAGING_STATUS_LABELS;
  protected readonly checklistLabels = CHECKLIST_LABELS;

  protected readonly form = this.fb.nonNullable.group({
    code: ['', [Validators.required, Validators.maxLength(40)]],
    batchId: ['', Validators.required],
    containerId: ['', Validators.required],
    // O volume é derivado no domínio; aqui só a quantidade de unidades.
    plannedUnits: this.fb.control<number | null>(null, [Validators.required, Validators.min(1)]),
    lineEquipmentId: ['', Validators.required],
    plannedStart: ['', Validators.required],
    plannedEnd: ['', Validators.required],
  });

  ngOnInit(): void {
    this.store.load();
    this.store.loadReferences();
  }

  protected plan(): void {
    if (this.form.invalid) {
      return;
    }
    const v = this.form.getRawValue();
    this.store.plan(
      {
        code: v.code,
        batchId: v.batchId,
        containerId: v.containerId,
        plannedUnits: v.plannedUnits!,
        lineEquipmentId: v.lineEquipmentId,
        plannedStart: new Date(v.plannedStart).toISOString(),
        plannedEnd: new Date(v.plannedEnd).toISOString(),
      },
      () => this.form.reset({ code: '', batchId: '', containerId: '', plannedUnits: null,
        lineEquipmentId: '', plannedStart: '', plannedEnd: '' }),
    );
  }

  protected confirm(planId: string, item: ChecklistItemCode): void {
    this.store.confirm(planId, item);
  }

  /** Cancelar devolve a embalagem ao estoque, então o motivo é exigido aqui. */
  protected cancel(plan: PackagingPlan): void {
    const reason = window.prompt(`Motivo do cancelamento do plano ${plan.code}:`);
    if (reason && reason.trim()) {
      this.store.cancel(plan.id, reason.trim());
    }
  }

  protected reserve(plan: PackagingPlan): void {
    this.store.reserve(plan.id);
  }

  // --- execução (PKG-003) ---

  /** A perda não é um campo: ela é derivada do que saiu do tanque menos o que foi envasado. */
  protected readonly runForm = this.fb.nonNullable.group({
    inputVolumeLiters: this.fb.control<number | null>(null, [Validators.required, Validators.min(0.001)]),
    producedUnits: this.fb.control<number | null>(null, [Validators.required, Validators.min(0)]),
    rejectedUnits: this.fb.control<number | null>(0, [Validators.required, Validators.min(0)]),
    note: [''],
  });

  protected openRun(plan: PackagingPlan): void {
    this.store.openRunOf(plan.id);
  }

  protected execute(plan: PackagingPlan): void {
    if (this.runForm.invalid) {
      return;
    }
    const v = this.runForm.getRawValue();
    const produced = v.producedUnits ?? 0;
    const rejected = v.rejectedUnits ?? 0;
    const total = produced + rejected;
    if (total <= 0) {
      return;
    }
    if (window.confirm(`Registrar o envase de ${plan.code}? ${total} embalagens serão consumidas do`
        + ' estoque e o plano será encerrado.')) {
      this.store.execute(plan.id, {
        inputVolumeLiters: v.inputVolumeLiters!,
        producedUnits: produced,
        rejectedUnits: rejected,
        note: v.note.trim() || null,
      });
    }
  }

  // --- carbonatação (PKG-002) ---

  protected readonly carbonationForm = this.fb.nonNullable.group({
    method: ['PRIMING' as CarbonationMethod, Validators.required],
    targetVolumes: this.fb.control<number | null>(null, [Validators.required, Validators.min(0.01)]),
    // No priming é a maior temperatura atingida após a fermentação; na forçada, a de aplicação do CO₂.
    referenceTempC: this.fb.control<number | null>(null, Validators.required),
    primingSugar: ['SUCROSE' as PrimingSugarCode],
  });

  protected readonly methodLabels = CARBONATION_METHOD_LABELS;
  protected readonly sugarLabels = PRIMING_SUGAR_LABELS;
  protected readonly sugarOptions = Object.keys(PRIMING_SUGAR_LABELS) as PrimingSugarCode[];
  protected readonly methodOptions = Object.keys(CARBONATION_METHOD_LABELS) as CarbonationMethod[];

  protected openCarbonation(plan: PackagingPlan): void {
    this.store.openCarbonationOf(plan.id);
  }

  protected preview(planId: string): void {
    const input = this.carbonationInput();
    if (input) {
      this.store.preview(planId, input);
    }
  }

  /** Confirmar é ato explícito: a prévia precisa ter sido calculada e revista antes. */
  protected confirmCarbonation(planId: string): void {
    const input = this.carbonationInput();
    if (!input) {
      return;
    }
    const recommendation = this.store.recommendation();
    const summary = recommendation?.primingSugarGrams != null
      ? `${recommendation.primingSugarGrams} g de açúcar`
      : `${recommendation?.pressureBar} bar`;
    if (window.confirm(`Confirmar a carbonatação (${summary}) para ${input.targetVolumes} volumes de CO₂?`)) {
      this.store.confirmCarbonation(planId, input);
    }
  }

  private carbonationInput(): CarbonationInput | null {
    if (this.carbonationForm.invalid) {
      return null;
    }
    const v = this.carbonationForm.getRawValue();
    return {
      method: v.method,
      targetVolumes: v.targetVolumes!,
      referenceTempC: v.referenceTempC!,
      primingSugar: v.method === 'PRIMING' ? v.primingSugar : null,
    };
  }

  protected blockersOf(planId: string) {
    return this.store.blockers()[planId] ?? [];
  }

  protected shortfallOf(planId: string) {
    return this.store.shortfall()[planId] ?? null;
  }
}
