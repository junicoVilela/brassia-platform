import { DatePipe, DecimalPipe } from '@angular/common';
import { ChangeDetectionStrategy, Component, OnInit, inject } from '@angular/core';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { AuthService } from '../../../../core/auth/auth.service';
import { EmptyStateComponent } from '../../../../shared/ui/empty-state.component';
import { LoadingIndicatorComponent } from '../../../../shared/ui/loading-indicator.component';
import { PageHeaderComponent } from '../../../../shared/ui/page-header.component';
import { MetrologyStore } from '../../data-access/metrology.store';
import {
  CALIBRATION_RESULT_LABELS,
  CalibrationResultCode,
  CurvePoint,
  FITNESS_LABELS,
  INSTRUMENT_TYPE_LABELS,
  Instrument,
  InstrumentTypeCode,
} from '../../domain/metrology.model';

@Component({
  selector: 'app-instruments-page',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [
    ReactiveFormsModule,
    DatePipe,
    DecimalPipe,
    PageHeaderComponent,
    EmptyStateComponent,
    LoadingIndicatorComponent,
  ],
  providers: [MetrologyStore],
  templateUrl: './instruments-page.component.html',
})
export class InstrumentsPageComponent implements OnInit {
  protected readonly store = inject(MetrologyStore);
  private readonly auth = inject(AuthService);
  private readonly fb = inject(FormBuilder);

  protected readonly canManage = this.auth.hasPermission('metrology.instrument.manage');
  protected readonly canManageStandards = this.auth.hasPermission('metrology.standard.manage');

  protected readonly fitnessLabels = FITNESS_LABELS;
  protected readonly typeLabels = INSTRUMENT_TYPE_LABELS;
  protected readonly resultLabels = CALIBRATION_RESULT_LABELS;
  protected readonly typeOptions = Object.keys(INSTRUMENT_TYPE_LABELS) as InstrumentTypeCode[];
  protected readonly resultOptions = Object.keys(CALIBRATION_RESULT_LABELS) as CalibrationResultCode[];

  protected readonly form = this.fb.nonNullable.group({
    code: ['', Validators.required],
    name: ['', Validators.required],
    type: ['THERMOMETER' as InstrumentTypeCode, Validators.required],
    rangeMin: [0, Validators.required],
    rangeMax: [100, Validators.required],
    resolution: [0.1, Validators.required],
    accuracy: [0.5, Validators.required],
    unit: ['°C', Validators.required],
    location: ['', Validators.required],
  });

  protected readonly standardForm = this.fb.nonNullable.group({
    code: ['', Validators.required],
    description: ['', Validators.required],
    certificateNumber: ['', Validators.required],
    issuer: ['', Validators.required],
    traceability: ['RBC', Validators.required],
    validUntil: ['', Validators.required],
  });

  protected readonly calibrationForm = this.fb.nonNullable.group({
    standardId: ['', Validators.required],
    performedOn: ['', Validators.required],
    dueOn: ['', Validators.required],
    performedBy: ['', Validators.required],
    certificateNumber: ['', Validators.required],
    result: ['APPROVED' as CalibrationResultCode, Validators.required],
    maxDeviation: [0, Validators.required],
    restriction: [''],
    note: [''],
    // Pontos do certificado, um por linha, no formato "verdadeiro;indicado". Texto livre porque
    // é o que o certificado traz — a validação de monotonia e de faixa é do domínio, no backend.
    curve: [''],
  });

  /** Correção de leitura (MTR-002): o bruto informado nunca é alterado. */
  protected readonly correctionForm = this.fb.nonNullable.group({
    rawValue: [0, Validators.required],
    unit: ['°C', Validators.required],
    sampleTempC: [null as number | null],
    calibrationTempC: [null as number | null],
    applyCurve: [true],
  });

  ngOnInit(): void {
    this.store.load();
  }

  protected register(): void {
    if (this.form.invalid) {
      return;
    }
    this.store.register(this.form.getRawValue(), () => this.form.reset());
  }

  protected registerStandard(): void {
    if (this.standardForm.invalid) {
      return;
    }
    this.store.registerStandard(this.standardForm.getRawValue(), () => this.standardForm.reset());
  }

  protected calibrate(instrument: Instrument): void {
    if (this.calibrationForm.invalid) {
      return;
    }
    const value = this.calibrationForm.getRawValue();
    const curve = this.parseCurve(value.curve);
    if (curve === null) {
      return;
    }
    this.store.calibrate(
      instrument.id,
      {
        ...value,
        restriction: value.result === 'APPROVED_WITH_RESTRICTION' ? value.restriction || null : null,
        note: value.note || null,
        curve: curve.length > 0 ? curve : null,
      },
      () => this.calibrationForm.reset({ result: 'APPROVED', maxDeviation: 0 }),
    );
  }

  /**
   * Lê os pontos do certificado. Devolve `null` quando o texto está malformado — aí o formulário
   * não envia nada, em vez de mandar uma curva pela metade ao backend.
   */
  private parseCurve(raw: string): CurvePoint[] | null {
    const linhas = raw.split('\n').map(l => l.trim()).filter(l => l.length > 0);
    const pontos: CurvePoint[] = [];
    for (const linha of linhas) {
      const [ref, med] = linha.split(';').map(v => Number(v.trim()));
      if (Number.isNaN(ref) || Number.isNaN(med)) {
        this.store.reportCurveFormat(linha);
        return null;
      }
      pontos.push({ reference: ref, measured: med });
    }
    return pontos;
  }

  protected correct(instrument: Instrument): void {
    if (this.correctionForm.invalid) {
      return;
    }
    const value = this.correctionForm.getRawValue();
    this.store.correct({
      instrumentId: instrument.id,
      sourceReadingId: null,
      ...value,
    });
  }

  protected block(instrument: Instrument): void {
    const reason = window.prompt('Motivo do bloqueio:');
    if (reason?.trim()) {
      this.store.setBlock(instrument, true, reason.trim());
    }
  }

  protected unblock(instrument: Instrument): void {
    this.store.setBlock(instrument, false, '');
  }

  protected retire(instrument: Instrument): void {
    const reason = window.prompt('Motivo da baixa (definitiva):');
    if (reason?.trim()) {
      this.store.retire(instrument, reason.trim());
    }
  }

  protected toggleCritical(instrument: Instrument): void {
    this.store.setCriticalUse(instrument, !instrument.criticalUse);
  }

  /** Classe do badge por aptidão: apto verde, impedimento vermelho, pendência neutra. */
  protected fitnessClass(instrument: Instrument): string {
    switch (instrument.fitness) {
      case 'FIT':
        return 'text-bg-success';
      case 'EXPIRED':
      case 'REJECTED':
      case 'BLOCKED':
        return 'text-bg-danger';
      default:
        return 'text-bg-secondary';
    }
  }
}
