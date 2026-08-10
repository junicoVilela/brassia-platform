import { DestroyRef, Injectable, computed, inject, signal } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { finalize } from 'rxjs';
import { ToastService } from '../../../core/notifications/toast.service';
import { Experiment, PlanExperimentRequest } from '../domain/experiment.model';
import { ExperimentsApi } from './experiments.api';

interface ExperimentError {
  status?: number;
  error?: { code?: string; detail?: string; differingFactors?: string[]; currentStatus?: string };
}

/**
 * Estado dos experimentos (EXP-001).
 *
 * <p><strong>A recusa por confundimento é explicada, não traduzida para "erro de validação".</strong> Quem
 * planejou um experimento com dois fatores diferentes não cometeu um erro de digitação: tomou uma decisão
 * de desenho que não se sustenta, e a mensagem precisa dizer qual é o problema — senão a correção provável
 * é remover um fator da lista em vez de igualar os dois lados.
 */
@Injectable()
export class ExperimentsStore {
  private readonly api = inject(ExperimentsApi);
  private readonly toast = inject(ToastService);
  private readonly destroyRef = inject(DestroyRef);

  readonly experiments = signal<Experiment[]>([]);
  readonly loading = signal(false);
  readonly loaded = signal(false);
  readonly error = signal<string | null>(null);

  readonly planning = signal(false);
  readonly planError = signal<string | null>(null);
  /** Os fatores que impedem o plano — a tela os destaca na própria lista. */
  readonly confoundedFactors = signal<string[]>([]);

  readonly active = computed(() =>
    this.experiments().filter(e => e.status === 'PLANNED' || e.status === 'RUNNING'),
  );

  /**
   * Concluídos e abandonados.
   *
   * Abandonados ficam visíveis de propósito: alguém já tentou aquilo e parou, e esconder faria a próxima
   * pessoa repetir a mesma tentativa sem saber que ela existiu.
   */
  readonly closed = computed(() =>
    this.experiments().filter(e => e.status === 'CONCLUDED' || e.status === 'ABANDONED'),
  );

  load(recipeId?: string): void {
    this.loading.set(true);
    this.error.set(null);
    this.api
      .list(recipeId)
      .pipe(
        takeUntilDestroyed(this.destroyRef),
        finalize(() => {
          this.loading.set(false);
          this.loaded.set(true);
        }),
      )
      .subscribe({
        next: experiments => this.experiments.set(experiments),
        error: () => this.error.set('Não foi possível carregar os experimentos.'),
      });
  }

  plan(request: PlanExperimentRequest, onSuccess: () => void): void {
    this.planning.set(true);
    this.planError.set(null);
    this.confoundedFactors.set([]);
    this.api
      .plan(request)
      .pipe(
        takeUntilDestroyed(this.destroyRef),
        finalize(() => this.planning.set(false)),
      )
      .subscribe({
        next: experiment => {
          // O aviso conta quantas limitações o desenho já carrega, porque é o momento em que ainda dá
          // para melhorá-lo — depois do experimento pronto, não dá mais.
          this.toast.success(
            `Experimento planejado isolando "${experiment.isolatedVariable.name}". ` +
              `O desenho carrega ${experiment.limitations.length} limitação(ões).`,
          );
          onSuccess();
          this.load(request.recipeId);
        },
        error: (e: ExperimentError) => this.handlePlanError(e),
      });
  }

  start(id: string, recipeId?: string): void {
    this.api
      .start(id)
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe({
        next: () => this.load(recipeId),
        error: (e: ExperimentError) => this.toast.error(this.messageFor(e)),
      });
  }

  conclude(id: string, supported: boolean, observation: string, recipeId?: string): void {
    this.api
      .conclude(id, { supported, observation })
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe({
        next: experiment => {
          this.toast.success(
            `Conclusão registrada com ${experiment.conclusion ? experiment.limitations.length : 0} ` +
              'limitação(ões) — elas acompanham o resultado em qualquer relatório.',
          );
          this.load(recipeId);
        },
        error: (e: ExperimentError) => this.toast.error(this.messageFor(e)),
      });
  }

  abandon(id: string, recipeId?: string): void {
    this.api
      .abandon(id)
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe({
        next: () => this.load(recipeId),
        error: (e: ExperimentError) => this.toast.error(this.messageFor(e)),
      });
  }

  private handlePlanError(e: ExperimentError): void {
    // `e.error` e não `e`: o HttpErrorResponse embrulha o corpo.
    if (e.error?.code === 'confounded_experiment') {
      this.confoundedFactors.set(e.error.differingFactors ?? []);
    }
    this.planError.set(this.messageFor(e));
  }

  private messageFor(e: ExperimentError): string {
    const code = e.error?.code;
    if (code === 'confounded_experiment') {
      const fatores = e.error?.differingFactors ?? [];
      return fatores.length === 0
        ? 'Controle e variante estão idênticos: não há variável em teste.'
        : `Mais de um fator difere (${fatores.join(', ')}). Com dois fatores, qualquer resultado tem ` +
            'duas explicações e nenhuma pode ser descartada. Iguale um dos lados.';
    }
    if (code === 'experiment_pair_already_active') {
      return (
        e.error?.detail ??
        'Estes dois lotes já estão em um experimento em andamento. Conclua ou abandone o anterior.'
      );
    }
    if (code === 'invalid_experiment_subject') {
      return e.error?.detail ?? 'Os lotes escolhidos não servem para este experimento.';
    }
    if (code === 'illegal_experiment_transition') {
      return (
        `O experimento está em ${e.error?.currentStatus ?? 'outro estado'} — provavelmente outra ` +
        'pessoa já o atualizou. Recarregue para ver o estado atual.'
      );
    }
    if (e.status === 403) {
      return 'Concluir um experimento é alçada própria: define o que a cervejaria passa a acreditar.';
    }
    return e.error?.detail ?? 'Não foi possível concluir a operação.';
  }
}
