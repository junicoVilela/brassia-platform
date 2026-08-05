import { DestroyRef, Injectable, computed, inject, signal } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { finalize } from 'rxjs';
import { ToastService } from '../../../core/notifications/toast.service';
import {
  AddSampleRequest,
  CreateSessionRequest,
  SensorySession,
  SessionRefusal,
  SessionResults,
  SubmitEvaluationRequest,
} from '../domain/sensory.model';
import { SensoryApi } from './sensory.api';

/** Corpo Problem Details das recusas sensoriais, como o backend as publica. */
interface SensoryError {
  status?: number;
  code?: string;
  detail?: string;
  session?: SessionRefusal;
  sample?: { blindCode: string };
}

/** Estado da análise sensorial (SEN-001). */
@Injectable()
export class SensoryStore {
  private readonly api = inject(SensoryApi);
  private readonly toast = inject(ToastService);
  private readonly destroyRef = inject(DestroyRef);

  readonly sessions = signal<SensorySession[]>([]);
  readonly loading = signal(false);
  readonly error = signal<string | null>(null);
  readonly submitting = signal(false);
  readonly actionError = signal<string | null>(null);

  readonly openSessionOf = signal<string | null>(null);
  readonly results = signal<SessionResults | null>(null);
  readonly resultsError = signal<string | null>(null);

  readonly empty = computed(() => !this.loading() && !this.error() && this.sessions().length === 0);

  /** Sessões que aceitam ficha agora — é o que o provador procura ao entrar na tela. */
  readonly openSessions = computed(() => this.sessions().filter(s => s.status === 'OPEN'));

  /**
   * Painéis com dispersão alta na última leitura de resultado. Não é sinal sobre a cerveja: é
   * sinal de que o painel precisa de calibração antes de a sessão servir para decidir.
   */
  readonly inconsistentBatches = computed(() =>
    (this.results()?.consistency ?? []).filter(c => c.difference >= 2),
  );

  load(): void {
    this.loading.set(true);
    this.error.set(null);
    this.api
      .sessions()
      .pipe(takeUntilDestroyed(this.destroyRef), finalize(() => this.loading.set(false)))
      .subscribe({
        next: list => this.sessions.set(list),
        error: () => this.error.set('Não foi possível carregar as sessões sensoriais.'),
      });
  }

  create(request: CreateSessionRequest, onSuccess?: () => void): void {
    this.run(this.api.create(request), 'Sessão criada como rascunho.', onSuccess);
  }

  addSample(sessionId: string, request: AddSampleRequest, onSuccess?: () => void): void {
    // O código cego é sorteado pelo backend; a tela não escolhe nem exibe intenção.
    this.run(this.api.addSample(sessionId, request), 'Amostra incluída com código cego.', onSuccess);
  }

  removeSample(sessionId: string, sampleId: string): void {
    this.run(this.api.removeSample(sessionId, sampleId), 'Amostra removida.');
  }

  open(session: SensorySession): void {
    this.run(this.api.open(session.id), `Sessão ${session.code} aberta para avaliação.`);
  }

  close(session: SensorySession): void {
    this.run(this.api.close(session.id), `Sessão ${session.code} encerrada; resultado revelado.`, () =>
      this.loadResults(session.id),
    );
  }

  submit(sessionId: string, request: SubmitEvaluationRequest, onSuccess?: () => void): void {
    this.run(this.api.submit(sessionId, request), 'Ficha enviada.', onSuccess);
  }

  toggleSession(session: SensorySession): void {
    if (this.openSessionOf() === session.id) {
      this.openSessionOf.set(null);
      this.results.set(null);
      return;
    }
    this.openSessionOf.set(session.id);
    this.results.set(null);
    this.resultsError.set(null);
    if (session.resultsAvailable) {
      this.loadResults(session.id);
    }
  }

  private loadResults(sessionId: string): void {
    this.api
      .results(sessionId)
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe({
        next: results => this.results.set(results),
        error: (e: SensoryError) => {
          // Pedir resultado antes do fechamento é comportamento esperado da regra, não falha.
          this.resultsError.set(
            e.code === 'results_not_available'
              ? 'O resultado só aparece quando a sessão é encerrada.'
              : 'Não foi possível carregar o resultado.',
          );
        },
      });
  }

  private run(
    call: import('rxjs').Observable<SensorySession>,
    message: string,
    onSuccess?: () => void,
  ): void {
    this.submitting.set(true);
    this.actionError.set(null);
    call
      .pipe(takeUntilDestroyed(this.destroyRef), finalize(() => this.submitting.set(false)))
      .subscribe({
        next: () => {
          this.toast.success(message);
          this.load();
          onSuccess?.();
        },
        error: (e: SensoryError) => {
          if (e.code === 'already_evaluated') {
            this.actionError.set(
              `Você já enviou ficha para a amostra ${e.sample?.blindCode}. A ficha é imutável.`,
            );
          } else if (e.code === 'session_not_open') {
            this.actionError.set(
              `A sessão está em ${e.session?.status} e não recebe fichas agora.`,
            );
          } else {
            this.actionError.set(e.detail ?? 'Não foi possível concluir a operação.');
          }
        },
      });
  }
}
