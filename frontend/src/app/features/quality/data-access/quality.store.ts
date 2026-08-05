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
  NonConformity,
  OpenNcRequest,
  PhaseOutOfOrder,
  RecordMeasurementRequest,
} from '../domain/quality.model';
import { QualityApi } from './quality.api';

/** Corpo Problem Details das recusas de qualidade, como o backend as publica. */
interface QualityError {
  status?: number;
  code?: string;
  detail?: string;
  controlPoint?: CriticalPointRefusal;
  nonConformity?: PhaseOutOfOrder;
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

  /** Não conformidades e o tratamento (QLT-002). */
  readonly nonConformities = signal<NonConformity[]>([]);
  readonly openNcOf = signal<string | null>(null);
  readonly ncError = signal<string | null>(null);
  readonly phaseRefusal = signal<PhaseOutOfOrder | null>(null);

  /** NC com alguma fase vencida — derivado pelo backend na data da consulta. */
  readonly overdueNcs = computed(() => this.nonConformities().filter(nc => nc.overdue));

  /** Prontas para encerrar: verificação eficaz registrada. */
  readonly closableNcs = computed(() => this.nonConformities().filter(nc => nc.closable));

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
    this.loadNonConformities();
  }

  private loadNonConformities(): void {
    this.api
      .nonConformities()
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe({
        next: list => this.nonConformities.set(list),
        error: () => this.nonConformities.set([]),
      });
  }

  toggleNc(nc: NonConformity): void {
    this.openNcOf.set(this.openNcOf() === nc.id ? null : nc.id);
  }

  openNonConformity(request: OpenNcRequest, onSuccess?: () => void): void {
    this.runNcCommand(this.api.openNc(request), 'Não conformidade aberta.', onSuccess);
  }

  contain(ncId: string, description: string): void {
    this.runNcCommand(this.api.contain(ncId, description), 'Contenção registrada.');
  }

  investigate(ncId: string, rootCause: string, method: string): void {
    this.runNcCommand(this.api.investigate(ncId, rootCause, method), 'Investigação registrada.');
  }

  planAction(
    ncId: string,
    request: { kind: string; description: string; owner: string; dueOn: string },
    onSuccess?: () => void,
  ): void {
    this.runNcCommand(this.api.planAction(ncId, request), 'Ação planejada.', onSuccess);
  }

  completeAction(ncId: string, actionId: string): void {
    this.runNcCommand(this.api.completeAction(ncId, actionId), 'Ação concluída.');
  }

  /**
   * Verificação ineficaz não é erro: é resultado válido que devolve a NC à fase de ação. Por isso
   * o aviso é diferente do sucesso — quem verificou precisa saber que o tratamento continua.
   */
  verify(ncId: string, effective: boolean, evidence: string): void {
    this.runNcCommand(
      this.api.verify(ncId, effective, evidence),
      effective
        ? 'Verificação eficaz: a não conformidade pode ser encerrada.'
        : 'Verificação ineficaz: planeje uma ação nova.',
    );
  }

  close(ncId: string): void {
    this.runNcCommand(this.api.closeNc(ncId), 'Não conformidade encerrada.');
  }

  private runNcCommand(
    call: import('rxjs').Observable<NonConformity>,
    message: string,
    onSuccess?: () => void,
  ): void {
    this.submitting.set(true);
    this.ncError.set(null);
    this.phaseRefusal.set(null);
    call
      .pipe(takeUntilDestroyed(this.destroyRef), finalize(() => this.submitting.set(false)))
      .subscribe({
        next: () => {
          this.toast.success(message);
          this.loadNonConformities();
          onSuccess?.();
        },
        error: (e: QualityError) => {
          // A recusa de fase diz em que fase está e o que se tentou fazer — informação acionável.
          if (e.code === 'nc_phase_out_of_order' && e.nonConformity) {
            this.phaseRefusal.set(e.nonConformity);
          } else {
            this.ncError.set(e.detail ?? 'Não foi possível concluir a operação.');
          }
        },
      });
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
          this.actionError.set(e.detail ?? 'Não foi possível criar o plano.'),
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
          this.actionError.set(e.detail ?? 'Não foi possível incluir o ponto.'),
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
          this.actionError.set(e.detail ?? 'Não foi possível remover o ponto.'),
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
          this.actionError.set(e.detail ?? 'Não foi possível publicar o plano.'),
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
          this.actionError.set(e.detail ?? 'Não foi possível criar a nova versão.'),
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
          if (e.code === 'instrument_not_fit' && e.controlPoint) {
            this.criticalRefusal.set(e.controlPoint);
          } else {
            this.measurementError.set(e.detail ?? 'Não foi possível registrar a medição.');
          }
        },
      });
  }
}
