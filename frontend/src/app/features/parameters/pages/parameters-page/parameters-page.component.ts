import { ChangeDetectionStrategy, Component, OnInit, effect, inject } from '@angular/core';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { AuthService } from '../../../../core/auth/auth.service';
import { LoadingIndicatorComponent } from '../../../../shared/ui/loading-indicator.component';
import { PageHeaderComponent } from '../../../../shared/ui/page-header.component';
import { ParametersStore } from '../../data-access/parameters.store';
import {
  INSTRUMENT_TYPE_LABELS,
  InstrumentTypeCode,
  SEVERITY_LABELS,
  SeverityCode,
} from '../../domain/parameters.model';

/**
 * Tela de parametrização por cervejaria (PRM-001).
 *
 * <p>Cinco políticas, cinco formulários, cinco botões. Cada uma vale contra o seu módulo e é salva
 * isolada — não há transação abrangendo as cinco, e fingir que há seria mentir para quem opera.
 */
@Component({
  selector: 'app-parameters-page',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [ReactiveFormsModule, PageHeaderComponent, LoadingIndicatorComponent],
  providers: [ParametersStore],
  templateUrl: './parameters-page.component.html',
})
export class ParametersPageComponent implements OnInit {
  protected readonly store = inject(ParametersStore);
  private readonly auth = inject(AuthService);
  private readonly fb = inject(FormBuilder);

  protected readonly canManageCleaning = this.auth.hasPermission('sanitation.policy.manage');
  protected readonly canManageGas = this.auth.hasPermission('gas.policy.manage');
  protected readonly canManageCalibration = this.auth.hasPermission('metrology.policy.manage');
  protected readonly canManageCapa = this.auth.hasPermission('quality.policy.manage');
  protected readonly canManageSensory = this.auth.hasPermission('sensory.policy.manage');

  protected readonly instrumentTypes = Object.keys(INSTRUMENT_TYPE_LABELS) as InstrumentTypeCode[];
  protected readonly instrumentTypeLabels = INSTRUMENT_TYPE_LABELS;
  protected readonly severities = Object.keys(SEVERITY_LABELS) as SeverityCode[];
  protected readonly severityLabels = SEVERITY_LABELS;

  /** Vazio significa "sem política": o campo em branco é um valor, não um esquecimento. */
  protected readonly cleaningForm = this.fb.group({
    validityHours: this.fb.control<number | null>(null, [Validators.min(1), Validators.max(720)]),
  });

  protected readonly gasForm = this.fb.group({
    requalificationMonths: this.fb.control<number | null>(null, [
      Validators.min(1),
      Validators.max(120),
    ]),
  });

  protected readonly calibrationForm = this.fb.group(
    Object.fromEntries(
      (Object.keys(INSTRUMENT_TYPE_LABELS) as InstrumentTypeCode[]).map(type => [
        type,
        this.fb.control<number | null>(null, [Validators.min(1), Validators.max(120)]),
      ]),
    ),
  );

  protected readonly capaForm = this.fb.group(
    Object.fromEntries(
      (Object.keys(SEVERITY_LABELS) as SeverityCode[]).flatMap(severity => [
        [`${severity}_containment`, this.fb.control<number | null>(null, [Validators.min(1)])],
        [`${severity}_investigation`, this.fb.control<number | null>(null, [Validators.min(1)])],
        [`${severity}_verification`, this.fb.control<number | null>(null, [Validators.min(1)])],
      ]),
    ),
  );

  protected readonly sensoryForm = this.fb.nonNullable.group({
    maxScore: [10, [Validators.required, Validators.min(3), Validators.max(100)]],
  });

  constructor() {
    // Os formulários só existem para editar o que veio; carregam sempre que a leitura muda.
    effect(() => {
      const parameters = this.store.parameters();
      if (!parameters) {
        return;
      }
      this.cleaningForm.setValue({ validityHours: parameters.cleaning.validityHours });
      this.gasForm.setValue({ requalificationMonths: parameters.gas.requalificationMonths });
      for (const type of this.instrumentTypes) {
        this.calibrationForm.controls[type].setValue(
          parameters.calibration.monthsByType[type] ?? null,
        );
      }
      for (const severity of this.severities) {
        const deadlines = parameters.capa.bySeverity[severity];
        this.capaForm.controls[`${severity}_containment`].setValue(
          deadlines?.containmentDays ?? null,
        );
        this.capaForm.controls[`${severity}_investigation`].setValue(
          deadlines?.investigationDays ?? null,
        );
        this.capaForm.controls[`${severity}_verification`].setValue(
          deadlines?.verificationDays ?? null,
        );
      }
      this.sensoryForm.setValue({ maxScore: parameters.sensory.maxScore });
    });
  }

  ngOnInit(): void {
    this.store.load();
  }

  protected saveCleaning(): void {
    if (this.cleaningForm.invalid) {
      return;
    }
    this.store.saveCleaning(this.cleaningForm.getRawValue().validityHours ?? null);
  }

  protected saveGas(): void {
    if (this.gasForm.invalid) {
      return;
    }
    this.store.saveGas(this.gasForm.getRawValue().requalificationMonths ?? null);
  }

  protected saveCalibration(): void {
    if (this.calibrationForm.invalid) {
      return;
    }
    const months: Partial<Record<InstrumentTypeCode, number>> = {};
    for (const type of this.instrumentTypes) {
      const value = this.calibrationForm.controls[type].value;
      if (value) {
        months[type] = value;
      }
    }
    this.store.saveCalibration(months);
  }

  protected saveCapa(): void {
    if (this.capaForm.invalid) {
      return;
    }
    const bySeverity: Record<string, { containmentDays: number; investigationDays: number; verificationDays: number } | null> = {};
    for (const severity of this.severities) {
      const containment = this.capaForm.controls[`${severity}_containment`].value;
      const investigation = this.capaForm.controls[`${severity}_investigation`].value;
      const verification = this.capaForm.controls[`${severity}_verification`].value;
      // Prazo pela metade não é política: ou as três fases têm data, ou a severidade fica de fora.
      bySeverity[severity] =
        containment && investigation && verification
          ? {
              containmentDays: containment,
              investigationDays: investigation,
              verificationDays: verification,
            }
          : null;
    }
    this.store.saveCapa(bySeverity);
  }

  protected saveSensory(): void {
    if (this.sensoryForm.invalid) {
      return;
    }
    this.store.saveSensory(this.sensoryForm.getRawValue().maxScore);
  }

  /** Uma severidade preenchida pela metade — o único erro que a tela julga sozinha. */
  protected incompleteSeverity(severity: SeverityCode): boolean {
    const values = [
      this.capaForm.controls[`${severity}_containment`].value,
      this.capaForm.controls[`${severity}_investigation`].value,
      this.capaForm.controls[`${severity}_verification`].value,
    ];
    return values.some(v => !!v) && values.some(v => !v);
  }
}
