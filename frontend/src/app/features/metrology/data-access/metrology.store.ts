import { DestroyRef, Injectable, computed, inject, signal } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { finalize } from 'rxjs';
import { ToastService } from '../../../core/notifications/toast.service';
import {
  Calibration,
  CalibrationStandard,
  CorrectReadingRequest,
  OutsideCurveRange,
  ReadingCorrection,
  Instrument,
  InstrumentNotFit,
  RecordCalibrationRequest,
  RegisterInstrumentRequest,
  RegisterStandardRequest,
  StandardExpired,
} from '../domain/metrology.model';
import { MetrologyApi } from './metrology.api';

/** Corpo Problem Details das recusas de metrologia, como o backend as publica. */
interface MetrologyError {
  status?: number;
  code?: string;
  detail?: string;
  instrument?: InstrumentNotFit;
  standard?: StandardExpired;
  curve?: OutsideCurveRange;
}

/** Estado do cadastro metrológico (MTR-001). */
@Injectable()
export class MetrologyStore {
  private readonly api = inject(MetrologyApi);
  private readonly toast = inject(ToastService);
  private readonly destroyRef = inject(DestroyRef);

  readonly instruments = signal<Instrument[]>([]);
  readonly standards = signal<CalibrationStandard[]>([]);

  readonly loading = signal(false);
  readonly error = signal<string | null>(null);
  readonly submitting = signal(false);
  readonly actionError = signal<string | null>(null);

  /** Histórico do instrumento aberto: certificado permanece, mesmo vencido. */
  readonly openHistoryOf = signal<string | null>(null);
  readonly calibrations = signal<Calibration[]>([]);
  readonly calibrationError = signal<string | null>(null);

  /** Recusas explicadas: por que o instrumento não serve, por que o padrão não calibra. */
  readonly notFit = signal<InstrumentNotFit | null>(null);
  readonly standardExpired = signal<StandardExpired | null>(null);

  /** Correções do instrumento aberto (MTR-002) e as recusas explicadas. */
  readonly corrections = signal<ReadingCorrection[]>([]);
  readonly outsideCurve = signal<OutsideCurveRange | null>(null);
  readonly correctionError = signal<string | null>(null);

  readonly empty = computed(() => !this.loading() && !this.error() && this.instruments().length === 0);

  /** Só padrão dentro da validade pode calibrar — vencido nem aparece como opção. */
  readonly validStandards = computed(() => this.standards().filter(s => !s.expired));

  /** Instrumentos designados para ponto crítico que deixaram de servir: é o alerta da tela. */
  readonly criticalAtRisk = computed(() =>
    this.instruments().filter(i => i.criticalUse && !i.fitForCriticalUse),
  );

  load(): void {
    this.loading.set(true);
    this.error.set(null);
    this.api
      .instruments()
      .pipe(takeUntilDestroyed(this.destroyRef), finalize(() => this.loading.set(false)))
      .subscribe({
        next: list => this.instruments.set(list),
        error: () => this.error.set('Não foi possível carregar os instrumentos.'),
      });
    this.api
      .standards()
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe({ next: list => this.standards.set(list), error: () => this.standards.set([]) });
  }

  register(request: RegisterInstrumentRequest, onSuccess?: () => void): void {
    this.submitting.set(true);
    this.actionError.set(null);
    this.api
      .register(request)
      .pipe(takeUntilDestroyed(this.destroyRef), finalize(() => this.submitting.set(false)))
      .subscribe({
        next: () => {
          this.toast.success('Instrumento cadastrado.');
          this.load();
          onSuccess?.();
        },
        error: (e: MetrologyError) =>
          this.actionError.set(e.detail ?? 'Não foi possível cadastrar o instrumento.'),
      });
  }

  registerStandard(request: RegisterStandardRequest, onSuccess?: () => void): void {
    this.submitting.set(true);
    this.actionError.set(null);
    this.api
      .registerStandard(request)
      .pipe(takeUntilDestroyed(this.destroyRef), finalize(() => this.submitting.set(false)))
      .subscribe({
        next: () => {
          this.toast.success('Padrão cadastrado.');
          this.load();
          onSuccess?.();
        },
        error: (e: MetrologyError) =>
          this.actionError.set(e.detail ?? 'Não foi possível cadastrar o padrão.'),
      });
  }

  setBlock(instrument: Instrument, blocked: boolean, reason: string): void {
    const call = blocked ? this.api.block(instrument.id, reason) : this.api.unblock(instrument.id);
    call.pipe(takeUntilDestroyed(this.destroyRef)).subscribe({
      next: () => {
        this.toast.success(blocked ? 'Instrumento bloqueado.' : 'Instrumento desbloqueado.');
        this.load();
      },
      error: (e: MetrologyError) =>
        this.actionError.set(e.detail ?? 'Não foi possível alterar o bloqueio.'),
    });
  }

  retire(instrument: Instrument, reason: string): void {
    this.api
      .retire(instrument.id, reason)
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe({
        next: () => {
          this.toast.success('Instrumento baixado.');
          this.load();
        },
        error: (e: MetrologyError) =>
          this.actionError.set(e.detail ?? 'Não foi possível dar baixa no instrumento.'),
      });
  }

  setCriticalUse(instrument: Instrument, criticalUse: boolean): void {
    this.notFit.set(null);
    this.api
      .setCriticalUse(instrument.id, criticalUse)
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe({
        next: () => {
          this.toast.success(
            criticalUse ? 'Designado para ponto crítico.' : 'Designação de ponto crítico removida.',
          );
          this.load();
        },
        error: (e: MetrologyError) => {
          // A recusa é informação acionável: dizemos qual aptidão barrou e até quando valia.
          if (e.code === 'instrument_not_fit' && e.instrument) {
            this.notFit.set(e.instrument);
          } else {
            this.actionError.set(e.detail ?? 'Não foi possível alterar a designação.');
          }
        },
      });
  }

  /** Formato do ponto do certificado errado: avisa sem tentar adivinhar o que a pessoa quis. */
  reportCurveFormat(linha: string): void {
    this.calibrationError.set(
      `Ponto da curva em formato inválido: "${linha}". Use "verdadeiro;indicado", um por linha.`,
    );
  }

  correct(request: CorrectReadingRequest): void {
    this.submitting.set(true);
    this.correctionError.set(null);
    this.outsideCurve.set(null);
    this.api
      .correct(request)
      .pipe(takeUntilDestroyed(this.destroyRef), finalize(() => this.submitting.set(false)))
      .subscribe({
        next: () => {
          this.toast.success('Correção registrada.');
          this.loadCorrections(request.instrumentId);
        },
        error: (e: MetrologyError) => {
          if (e.code === 'outside_curve_range' && e.curve) {
            this.outsideCurve.set(e.curve);
          } else {
            this.correctionError.set(e.detail ?? 'Não foi possível registrar a correção.');
          }
        },
      });
  }

  private loadCorrections(instrumentId: string): void {
    this.api
      .corrections(instrumentId)
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe({ next: list => this.corrections.set(list), error: () => this.corrections.set([]) });
  }

  toggleHistory(instrument: Instrument): void {
    if (this.openHistoryOf() === instrument.id) {
      this.openHistoryOf.set(null);
      this.calibrations.set([]);
      this.corrections.set([]);
      return;
    }
    this.openHistoryOf.set(instrument.id);
    this.loadCorrections(instrument.id);
    this.calibrationError.set(null);
    this.api
      .calibrations(instrument.id)
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe({
        next: list => this.calibrations.set(list),
        error: () => this.calibrationError.set('Não foi possível carregar o histórico.'),
      });
  }

  calibrate(instrumentId: string, request: RecordCalibrationRequest, onSuccess?: () => void): void {
    this.submitting.set(true);
    this.calibrationError.set(null);
    this.standardExpired.set(null);
    this.api
      .calibrate(instrumentId, request)
      .pipe(takeUntilDestroyed(this.destroyRef), finalize(() => this.submitting.set(false)))
      .subscribe({
        next: () => {
          this.toast.success('Calibração registrada.');
          this.load();
          this.openHistoryOf.set(null);
          onSuccess?.();
        },
        error: (e: MetrologyError) => {
          if (e.code === 'standard_expired' && e.standard) {
            this.standardExpired.set(e.standard);
          } else {
            this.calibrationError.set(e.detail ?? 'Não foi possível registrar a calibração.');
          }
        },
      });
  }
}
