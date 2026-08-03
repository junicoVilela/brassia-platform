import { DestroyRef, Injectable, computed, inject, signal } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { finalize } from 'rxjs';
import { ToastService } from '../../../core/notifications/toast.service';
import {
  AddPointRequest,
  ControlPlan,
  CreatePlanRequest,
  CriticalPointRefusal,
  Deviation,
  Measurement,
  RecordMeasurementRequest,
} from '../domain/quality.model';
import { QualityApi } from './quality.api';

/** Corpo Problem Details das recusas de qualidade, como o backend as publica. */
interface QualityError {
  status?: number;
  error?: { code?: string; detail?: string; controlPoint?: CriticalPointRefusal };
}

/** Estado do plano de controle (QLT-001). */
@Injectable()
export class QualityStore {
  private readonly api = inject(QualityApi);
  private readonly toast = inject(ToastService);
  private readonly destroyRef = inject(DestroyRef);

  readonly plans = signal<ControlPlan[]>([]);
  readonly deviations = signal<Deviation[]>([]);

  readonly loading = signal(false);
  readonly error = signal<string | null>(null);
  readonly submitting = signal(false);
  readonly actionError = signal<string | null>(null);

  readonly openPlanOf = signal<string | null>(null);
  readonly measurements = signal<Measurement[]>([]);

  /** Recusa explicada do ponto crítico: qual instrumento e por que não serve. */
  readonly criticalRefusal = signal<CriticalPointRefusal | null>(null);
  readonly measurementError = signal<string | null>(null);

  readonly empty = computed(() => !this.loading() && !this.error() && this.plans().length === 0);

  /** Só plano publicado julga medição — o rascunho não aparece como opção de medir. */
  readonly publishedPlans = computed(() => this.plans().filter(p => p.status === 'PUBLISHED'));

  /** Desvios críticos e graves em aberto: é o que a tela precisa mostrar primeiro. */
  readonly severeDeviations = computed(() => this.deviations().filter(d => d.severity !== 'MINOR'));

  load(): void {
    this.loading.set(true);
    this.error.set(null);
    this.api
      .plans()
      .pipe(takeUntilDestroyed(this.destroyRef), finalize(() => this.loading.set(false)))
      .subscribe({
        next: list => this.plans.set(list),
        error: () => this.error.set('Não foi possível carregar os planos de controle.'),
      });
    this.api
      .deviations()
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe({ next: list => this.deviations.set(list), error: () => this.deviations.set([]) });
  }

  create(request: CreatePlanRequest, onSuccess?: () => void): void {
    this.submitting.set(true);
    this.actionError.set(null);
    this.api
      .create(request)
      .pipe(takeUntilDestroyed(this.destroyRef), finalize(() => this.submitting.set(false)))
      .subscribe({
        next: () => {
          this.toast.success('Plano criado como rascunho.');
          this.load();
          onSuccess?.();
        },
        error: (e: QualityError) =>
          this.actionError.set(e.error?.detail ?? 'Não foi possível criar o plano.'),
      });
  }

  addPoint(planId: string, request: AddPointRequest, onSuccess?: () => void): void {
    this.submitting.set(true);
    this.actionError.set(null);
    this.api
      .addPoint(planId, request)
      .pipe(takeUntilDestroyed(this.destroyRef), finalize(() => this.submitting.set(false)))
      .subscribe({
        next: () => {
          this.toast.success('Ponto de controle incluído.');
          this.load();
          onSuccess?.();
        },
        error: (e: QualityError) =>
          this.actionError.set(e.error?.detail ?? 'Não foi possível incluir o ponto.'),
      });
  }

  removePoint(planId: string, pointId: string): void {
    this.api
      .removePoint(planId, pointId)
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe({
        next: () => {
          this.toast.success('Ponto removido.');
          this.load();
        },
        error: (e: QualityError) =>
          this.actionError.set(e.error?.detail ?? 'Não foi possível remover o ponto.'),
      });
  }

  publish(plan: ControlPlan): void {
    this.api
      .publish(plan.id)
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe({
        next: () => {
          this.toast.success(`Plano ${plan.code} publicado na versão ${plan.version}.`);
          this.load();
        },
        error: (e: QualityError) =>
          this.actionError.set(e.error?.detail ?? 'Não foi possível publicar o plano.'),
      });
  }

  newVersion(plan: ControlPlan): void {
    this.api
      .newVersion(plan.id)
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe({
        next: () => {
          this.toast.success('Nova versão criada como rascunho.');
          this.load();
        },
        error: (e: QualityError) =>
          this.actionError.set(e.error?.detail ?? 'Não foi possível criar a nova versão.'),
      });
  }

  togglePlan(plan: ControlPlan): void {
    if (this.openPlanOf() === plan.id) {
      this.openPlanOf.set(null);
      this.measurements.set([]);
      return;
    }
    this.openPlanOf.set(plan.id);
    this.api
      .measurements(plan.id)
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe({ next: list => this.measurements.set(list), error: () => this.measurements.set([]) });
  }

  measure(request: RecordMeasurementRequest, onSuccess?: () => void): void {
    this.submitting.set(true);
    this.measurementError.set(null);
    this.criticalRefusal.set(null);
    this.api
      .measure(request)
      .pipe(takeUntilDestroyed(this.destroyRef), finalize(() => this.submitting.set(false)))
      .subscribe({
        next: outcome => {
          // O desvio precisa aparecer na hora, com a ação: descobrir numa listagem depois é tarde.
          if (outcome.deviation) {
            this.toast.error(
              `Desvio ${outcome.deviation.severityLabel}: ${outcome.deviation.description}. ` +
                `Ação: ${outcome.deviation.action}`,
            );
          } else {
            this.toast.success('Medição dentro da faixa.');
          }
          this.load();
          onSuccess?.();
        },
        error: (e: QualityError) => {
          if (e.error?.code === 'instrument_not_fit' && e.error.controlPoint) {
            this.criticalRefusal.set(e.error.controlPoint);
          } else {
            this.measurementError.set(e.error?.detail ?? 'Não foi possível registrar a medição.');
          }
        },
      });
  }
}
