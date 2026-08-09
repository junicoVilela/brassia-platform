import { DestroyRef, Injectable, computed, inject, signal } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { finalize } from 'rxjs';
import { CommandProposal, PERMISSION_LABELS } from '../domain/proposal.model';
import { AiApi } from './ai.api';
import { BatchOption, BatchesApi } from './batches.api';

interface ProposalError {
  status?: number;
  code?: string;
  detail?: string;
}

/**
 * Estado das propostas de comando (AIA-003).
 *
 * <p><strong>Nada aqui executa nada.</strong> A store pede propostas, confirma e descarta — e confirmar
 * registra a decisão autorizada; o comando em si vive no módulo dono da ação, e a proposta carrega a rota
 * para lá.
 *
 * <p>Depois de qualquer decisão a lista é recarregada em vez de remendada em memória. Uma decisão que outra
 * pessoa tomou entre a leitura e o clique só aparece assim, e uma tela que remenda localmente mostraria o
 * estado que ela imaginou em vez do que o banco tem.
 */
@Injectable()
export class ProposalStore {
  private readonly api = inject(AiApi);
  private readonly batchesApi = inject(BatchesApi);
  private readonly destroyRef = inject(DestroyRef);

  readonly proposals = signal<CommandProposal[]>([]);
  readonly batches = signal<BatchOption[]>([]);
  readonly loading = signal(false);
  readonly error = signal<string | null>(null);

  readonly proposing = signal<string | null>(null);
  readonly deciding = signal<string | null>(null);
  readonly actionError = signal<string | null>(null);
  readonly lastProposed = signal<number | null>(null);

  /** Pendentes no prazo primeiro: é o que pede ação de quem abriu a tela. */
  readonly awaiting = computed(() =>
    this.proposals().filter(proposal => proposal.status === 'PENDING' && !proposal.expired),
  );

  /**
   * Vencidas sem decisão.
   *
   * Separadas de propósito: uma pendente vencida não é decisão adiada, é oferta que caducou — e misturá-la
   * com as vigentes faria a lista de "o que decidir hoje" crescer com coisa que não se decide mais.
   */
  readonly expired = computed(() =>
    this.proposals().filter(proposal => proposal.status === 'PENDING' && proposal.expired),
  );

  readonly decided = computed(() => this.proposals().filter(proposal => proposal.status !== 'PENDING'));

  load(): void {
    this.loading.set(true);
    this.error.set(null);
    this.api
      .proposals()
      .pipe(
        takeUntilDestroyed(this.destroyRef),
        finalize(() => this.loading.set(false)),
      )
      .subscribe({
        next: proposals => this.proposals.set(proposals),
        error: (e: ProposalError) => this.error.set(this.messageFor(e)),
      });
    this.batchesApi
      .batches()
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe({
        next: batches => this.batches.set(batches),
        error: () => this.batches.set([]),
      });
  }

  propose(batchId: string): void {
    this.proposing.set(batchId);
    this.actionError.set(null);
    this.lastProposed.set(null);
    this.api
      .propose(batchId)
      .pipe(
        takeUntilDestroyed(this.destroyRef),
        finalize(() => this.proposing.set(null)),
      )
      .subscribe({
        next: proposed => {
          // Zero é resposta legítima, e a tela precisa dizer isso em vez de parecer que nada aconteceu.
          this.lastProposed.set(proposed.length);
          this.load();
        },
        error: (e: ProposalError) => this.actionError.set(this.messageFor(e)),
      });
  }

  accept(proposalId: string, note?: string): void {
    this.decide(proposalId, this.api.accept(proposalId, note));
  }

  reject(proposalId: string, note?: string): void {
    this.decide(proposalId, this.api.reject(proposalId, note));
  }

  private decide(proposalId: string, request: ReturnType<AiApi['accept']>): void {
    this.deciding.set(proposalId);
    this.actionError.set(null);
    this.lastProposed.set(null);
    request
      .pipe(
        takeUntilDestroyed(this.destroyRef),
        finalize(() => this.deciding.set(null)),
      )
      .subscribe({
        next: () => this.load(),
        error: (e: ProposalError) => {
          this.actionError.set(this.messageFor(e));
          // Recarrega mesmo no erro: conflito e vencimento significam que o estado na tela está velho.
          this.load();
        },
      });
  }

  /** Nomeia a alçada que falta em vez de dizer "sem permissão". */
  missingPermissionLabel(proposal: CommandProposal): string {
    return PERMISSION_LABELS[proposal.requiredPermission] ?? proposal.requiredPermission;
  }

  private messageFor(e: ProposalError): string {
    switch (e.code) {
      case 'unknown_proposal':
        return 'Esta proposta não existe nesta cervejaria.';
      case 'proposal_not_pending':
        return 'Outra pessoa já decidiu esta proposta. A lista foi atualizada.';
      case 'proposal_expired':
        return 'O prazo desta proposta venceu. Peça uma nova, sobre os fatos de agora.';
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
      // O caso mais comum, e o mais importante de explicar: pedir proposta não dá direito de confirmar.
      return 'Confirmar exige a alçada do comando proposto — pedir a proposta não dá esse direito.';
    }
    return e.detail ?? 'Não foi possível concluir a operação.';
  }
}
