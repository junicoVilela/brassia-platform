import { DestroyRef, Injectable, computed, inject, signal } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { finalize } from 'rxjs';
import { GroundedAnswer } from '../domain/answer.model';
import { AiApi } from './ai.api';

interface CopilotError {
  status?: number;
  code?: string;
  detail?: string;
}

/**
 * Estado do copiloto (RAG-002).
 *
 * <p>Duas distinções que o estado precisa manter, e que a interface depende delas:
 *
 * <p><strong>"Ainda não perguntei" não é "não achei".</strong> Antes da primeira pergunta não há resposta
 * nenhuma; depois, uma resposta com `answered: false` é resposta legítima que declara limitação.
 *
 * <p><strong>Resposta que não sustenta não é erro de sistema.</strong> Quando as citações não conferem, o
 * backend devolve 200 com `answered: false` e o motivo — não uma falha. Tratar como erro faria a pessoa
 * tentar de novo esperando outro resultado, quando o que ela precisa é de outra fonte indexada.
 */
@Injectable()
export class CopilotStore {
  private readonly api = inject(AiApi);
  private readonly destroyRef = inject(DestroyRef);

  readonly answer = signal<GroundedAnswer | null>(null);
  readonly asking = signal(false);
  readonly error = signal<string | null>(null);
  readonly lastQuestion = signal<string | null>(null);

  /** Respondeu de fato, com fonte conferida. */
  readonly grounded = computed(() => this.answer()?.answered === true);

  /**
   * Não respondeu porque não havia fonte nenhuma — diferente de não ter conseguido sustentar.
   * A primeira pede indexar documento; a segunda pede olhar o prompt.
   */
  readonly withoutSources = computed(() => {
    const answer = this.answer();
    return answer !== null && !answer.answered && answer.consultedSources === 0;
  });

  readonly unsupported = computed(() => {
    const answer = this.answer();
    return answer !== null && !answer.answered && answer.consultedSources > 0;
  });

  ask(question: string, onDate: string | null): void {
    this.asking.set(true);
    this.error.set(null);
    this.answer.set(null);
    this.lastQuestion.set(question);
    this.api
      .ask({ question, onDate, equipmentId: null })
      .pipe(
        takeUntilDestroyed(this.destroyRef),
        finalize(() => this.asking.set(false)),
      )
      .subscribe({
        next: answer => this.answer.set(answer),
        error: (e: CopilotError) => this.error.set(this.messageFor(e)),
      });
  }

  private messageFor(e: CopilotError): string {
    switch (e.code) {
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
      return 'Perguntar ao copiloto é alçada própria, separada de consultar a base.';
    }
    return e.detail ?? 'Não foi possível concluir a operação.';
  }
}
