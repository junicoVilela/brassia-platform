import { DestroyRef, Injectable, computed, inject, signal } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { finalize, forkJoin } from 'rxjs';
import { Assessment, FactView } from '../domain/assessment.model';
import { AiApi } from './ai.api';
import { BatchOption, BatchesApi } from './batches.api';

interface AssessmentError {
  status?: number;
  code?: string;
  detail?: string;
}

/**
 * Estado da avaliação de lote (AIA-002).
 *
 * <p>Os fatos ficam disponíveis mesmo quando a avaliação não é utilizável, e é deliberado: eles são do
 * domínio e valem independentemente do que o modelo disse. Uma avaliação descartada por número inventado
 * ainda deixa a pessoa com todos os números calculados na tela — o que já é útil.
 */
@Injectable()
export class AssessmentStore {
  private readonly api = inject(AiApi);
  private readonly batchesApi = inject(BatchesApi);
  private readonly destroyRef = inject(DestroyRef);

  readonly batches = signal<BatchOption[]>([]);
  readonly loading = signal(false);
  readonly error = signal<string | null>(null);

  readonly assessment = signal<Assessment | null>(null);
  readonly assessing = signal<string | null>(null);
  readonly assessError = signal<string | null>(null);
  readonly selectedBatch = signal<string | null>(null);

  /** Fatos conhecidos e ausentes, separados: a ausência é informação e merece leitura própria. */
  readonly knownFacts = computed<FactView[]>(
    () => this.assessment()?.facts.filter(fact => fact.available) ?? [],
  );

  readonly absentFacts = computed<FactView[]>(
    () => this.assessment()?.facts.filter(fact => !fact.available) ?? [],
  );

  /** Avaliação que não sobreviveu à conferência: nada do que o modelo disse pôde ser usado. */
  readonly unusable = computed(() => {
    const assessment = this.assessment();
    return assessment !== null && !assessment.usable;
  });

  load(): void {
    this.loading.set(true);
    this.error.set(null);
    forkJoin({ batches: this.batchesApi.batches() })
      .pipe(
        takeUntilDestroyed(this.destroyRef),
        finalize(() => this.loading.set(false)),
      )
      .subscribe({
        next: ({ batches }) => this.batches.set(batches),
        error: () => this.error.set('Não foi possível carregar os lotes.'),
      });
  }

  assess(batchId: string): void {
    this.assessing.set(batchId);
    this.assessError.set(null);
    this.assessment.set(null);
    this.selectedBatch.set(batchId);
    this.api
      .assess(batchId)
      .pipe(
        takeUntilDestroyed(this.destroyRef),
        finalize(() => this.assessing.set(null)),
      )
      .subscribe({
        next: assessment => this.assessment.set(assessment),
        error: (e: AssessmentError) => this.assessError.set(this.messageFor(e)),
      });
  }

  private messageFor(e: AssessmentError): string {
    switch (e.code) {
      case 'unknown_batch':
        return 'Este lote não existe nesta cervejaria.';
      case 'ai_provider_disabled':
        return 'Esta instalação não tem copiloto de IA habilitado.';
      case 'ai_provider_unavailable':
        return 'O provedor de IA não respondeu. Tente novamente em alguns instantes.';
      case 'ai_budget_exceeded':
        return 'O orçamento de IA deste mês foi esgotado. Suba o teto ou aguarde o próximo mês.';
      case 'ai_response_rejected':
        return 'O modelo respondeu fora do formato exigido e a resposta foi recusada inteira.';
      default:
        break;
    }
    if (e.status === 403) {
      return 'Avaliar um lote é alçada própria, separada de perguntar ao copiloto.';
    }
    return e.detail ?? 'Não foi possível concluir a operação.';
  }
}
