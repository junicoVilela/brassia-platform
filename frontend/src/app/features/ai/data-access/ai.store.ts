import { DestroyRef, Injectable, computed, inject, signal } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { finalize } from 'rxjs';
import { ToastService } from '../../../core/notifications/toast.service';
import { GatewayStatus, ProbeResult } from '../domain/gateway.model';
import { AiApi } from './ai.api';

interface AiError {
  status?: number;
  code?: string;
  detail?: string;
}

/**
 * Estado do gateway de IA (AIA-001).
 *
 * <p>Depois de qualquer comando o status é relido do servidor em vez de remendado localmente. O
 * motivo é o gasto: uma verificação move o consumo do mês, e uma cópia local somaria o custo que a
 * UI *acha* que a chamada teve em vez do que ela teve. Aqui o número que aparece é sempre o do
 * ledger.
 */
@Injectable()
export class AiStore {
  private readonly api = inject(AiApi);
  private readonly toast = inject(ToastService);
  private readonly destroyRef = inject(DestroyRef);

  readonly status = signal<GatewayStatus | null>(null);
  readonly loading = signal(false);
  readonly error = signal<string | null>(null);

  readonly probing = signal(false);
  readonly probeResult = signal<ProbeResult | null>(null);
  readonly probeError = signal<string | null>(null);

  readonly savingBudget = signal(false);
  readonly budgetError = signal<string | null>(null);

  /** Quanto do teto já foi, em porcentagem, para a barra de consumo. */
  readonly spentPercent = computed(() => {
    const budget = this.status()?.budget;
    if (!budget || budget.monthlyLimit <= 0) {
      return 0;
    }
    return Math.min(100, Math.round((budget.spentThisMonth / budget.monthlyLimit) * 100));
  });

  /** O modelo preferido — o primeiro da cadeia; os outros são fallback. */
  readonly primaryModel = computed(() => this.status()?.models[0] ?? null);
  readonly fallbackModels = computed(() => this.status()?.models.slice(1) ?? []);

  load(): void {
    this.loading.set(true);
    this.error.set(null);
    this.api
      .status()
      .pipe(
        takeUntilDestroyed(this.destroyRef),
        finalize(() => this.loading.set(false)),
      )
      .subscribe({
        next: status => this.status.set(status),
        error: () => this.error.set('Não foi possível carregar o estado do copiloto.'),
      });
  }

  probe(): void {
    this.probing.set(true);
    this.probeError.set(null);
    this.probeResult.set(null);
    this.api
      .probe()
      .pipe(
        takeUntilDestroyed(this.destroyRef),
        finalize(() => this.probing.set(false)),
      )
      .subscribe({
        next: result => {
          this.probeResult.set(result);
          this.toast.success('O copiloto respondeu no formato esperado.');
          this.load();
        },
        error: (e: AiError) => {
          this.probeError.set(this.messageFor(e));
          // A tentativa recusada também virou linha no ledger: reler mostra isso.
          this.load();
        },
      });
  }

  redefineBudget(monthlyLimit: number): void {
    const current = this.status();
    if (!current) {
      return;
    }
    this.savingBudget.set(true);
    this.budgetError.set(null);
    this.api
      .redefineBudget(monthlyLimit, current.budget.version)
      .pipe(
        takeUntilDestroyed(this.destroyRef),
        finalize(() => this.savingBudget.set(false)),
      )
      .subscribe({
        next: () => {
          this.toast.success('Teto de gasto atualizado.');
          this.load();
        },
        error: (e: AiError) => {
          this.budgetError.set(this.messageFor(e));
          if (e.status === 409) {
            // Recarrega para que a próxima tentativa saia com a versão de quem gravou antes.
            this.load();
          }
        },
      });
  }

  private messageFor(e: AiError): string {
    switch (e.code) {
      case 'ai_provider_disabled':
        return 'Esta instalação não tem copiloto de IA habilitado.';
      case 'ai_provider_unavailable':
        return 'O provedor de IA não respondeu. Tente novamente em alguns instantes.';
      case 'ai_budget_exceeded':
        return 'O orçamento de IA deste mês foi esgotado. Suba o teto ou aguarde o próximo mês.';
      case 'ai_response_rejected':
        return 'O modelo respondeu fora do formato exigido e a resposta foi recusada.';
      case 'ai_budget_stale':
        return 'O teto foi alterado por outra pessoa. Confira o valor atual e tente novamente.';
      default:
        break;
    }
    if (e.status === 403) {
      return 'Esta operação tem alçada própria, separada da de consultar.';
    }
    return e.detail ?? 'Não foi possível concluir a operação.';
  }
}
