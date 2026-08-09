import { HttpErrorResponse } from '@angular/common/http';
import { Injectable, computed, effect, inject, signal } from '@angular/core';
import { firstValueFrom } from 'rxjs';
import { AuthService } from '../../../core/auth/auth.service';
import { NetworkStatusService } from '../../../core/offline/network-status.service';
import { OfflineQueueStore, QueuedEntry } from '../../../core/offline/offline-queue.store';
import { ToastService } from '../../../core/notifications/toast.service';
import { BatchesApi } from './batches.api';

/**
 * Drena a fila de apontamentos quando a rede volta (PWA-002).
 *
 * <p><strong>Os três desfechos de um envio são tratados de formas diferentes, e a diferença é a história:</strong>
 *
 * <ul>
 *   <li><strong>201 ou 200</strong> — registrado. O 200 significa que o servidor reconheceu o reenvio como
 *       o mesmo apontamento; para a fila, os dois querem dizer "pode tirar daqui".
 *   <li><strong>Falha de rede ou 5xx</strong> — transitória. Conta a tentativa e mantém na fila: desistir
 *       perderia o apontamento de quem estava sem rede, que é a razão de a fila existir.
 *   <li><strong>409 ou 4xx de estado</strong> — conflito. Sai do ciclo automático e **espera decisão
 *       humana**. Insistir produziria o mesmo "não" mais quatro vezes; descartar perderia o apontamento;
 *       aplicar à força sobrescreveria o que outra pessoa fez.
 * </ul>
 *
 * <p>O envio é <strong>sequencial</strong>, não paralelo. Apontamentos do mesmo lote têm ordem — uma
 * medição depois de uma etapa concluída significa outra coisa que antes —, e disparar tudo de uma vez
 * entregaria numa ordem que ninguém escolheu.
 */
@Injectable({ providedIn: 'root' })
export class OfflineQueueFacade {
  private readonly queue = inject(OfflineQueueStore);
  private readonly api = inject(BatchesApi);
  private readonly auth = inject(AuthService);
  private readonly network = inject(NetworkStatusService);
  private readonly toast = inject(ToastService);

  private readonly flushing = signal(false);

  readonly online = this.network.online;
  readonly entries = this.queue.entries;
  readonly pending = this.queue.pending;
  readonly conflicts = this.queue.conflicts;
  readonly hasPending = this.queue.hasPending;
  readonly hasConflicts = this.queue.hasConflicts;
  readonly syncing = this.flushing.asReadonly();
  readonly pendingCount = computed(() => this.pending().length);

  constructor() {
    // A volta da rede é o gatilho natural: quem registrou sem rede não deve precisar lembrar de sincronizar.
    effect(() => {
      if (this.network.online() && this.queue.hasPending() && !this.flushing()) {
        void this.flush();
      }
    });
  }

  load(): void {
    const identity = this.identity();
    if (identity) {
      this.queue.load(identity.userId, identity.breweryId);
    }
  }

  /** Registra um apontamento para envio posterior e devolve a chave que viajará com ele. */
  enqueue(batchId: string, batchCode: string, payload: Record<string, unknown>): string | null {
    const identity = this.identity();
    if (!identity) {
      return null;
    }
    const entry = this.queue.enqueue(
      identity.userId,
      identity.breweryId,
      batchId,
      batchCode,
      payload,
    );
    return entry.clientRequestId;
  }

  discardConflict(clientRequestId: string): void {
    const identity = this.identity();
    if (identity) {
      this.queue.discard(identity.userId, identity.breweryId, clientRequestId);
    }
  }

  /** Tenta enviar tudo que está pendente, um de cada vez. */
  async flush(): Promise<void> {
    const identity = this.identity();
    if (!identity || this.flushing()) {
      return;
    }
    this.flushing.set(true);
    let enviados = 0;
    try {
      for (const entry of [...this.queue.pending()]) {
        const desfecho = await this.send(identity, entry);
        if (desfecho === 'sent') {
          enviados++;
        }
        if (desfecho === 'offline') {
          // Rede caiu no meio da drenagem. Parar aqui evita gastar tentativas de todos os itens contra
          // uma rede que já se sabe indisponível.
          break;
        }
      }
    } finally {
      this.flushing.set(false);
    }
    if (enviados > 0) {
      this.toast.success(
        enviados === 1 ? '1 apontamento sincronizado.' : `${enviados} apontamentos sincronizados.`,
      );
    }
    if (this.queue.hasConflicts()) {
      this.toast.error('Há apontamentos em conflito aguardando sua decisão.');
    }
  }

  private async send(
    identity: { userId: string; breweryId: string },
    entry: QueuedEntry,
  ): Promise<'sent' | 'retry' | 'conflict' | 'offline'> {
    try {
      await firstValueFrom(
        this.api.recordMeasurement(entry.batchId, {
          ...entry.payload,
          clientRequestId: entry.clientRequestId,
        } as never),
      );
      this.queue.acknowledge(identity.userId, identity.breweryId, entry.clientRequestId);
      return 'sent';
    } catch (error) {
      const response = error as HttpErrorResponse;
      // status 0 é "não saiu daqui": rede caiu, DNS, servidor inalcançável.
      if (response.status === 0) {
        this.queue.registerAttempt(identity.userId, identity.breweryId, entry.clientRequestId);
        return 'offline';
      }
      if (this.isConflict(response.status)) {
        this.queue.markConflict(
          identity.userId,
          identity.breweryId,
          entry.clientRequestId,
          this.reasonFor(response),
        );
        return 'conflict';
      }
      this.queue.registerAttempt(identity.userId, identity.breweryId, entry.clientRequestId);
      return 'retry';
    }
  }

  /**
   * O que é conflito e o que é falha transitória.
   *
   * <p>409 é o caso claro — o estado mudou. 400 e 422 entram porque um apontamento recusado por conteúdo
   * não passa a ser aceito na décima tentativa: insistir só gastaria a fila. 5xx fica de fora
   * deliberadamente: erro do servidor costuma passar, e tratá-lo como conflito jogaria na mão de quem
   * opera uma decisão que era só esperar.
   */
  private isConflict(status: number): boolean {
    return status === 409 || status === 400 || status === 422;
  }

  private reasonFor(response: HttpErrorResponse): string {
    const detail = (response.error as { detail?: string } | undefined)?.detail;
    if (detail) {
      return detail;
    }
    return response.status === 409
      ? 'O estado mudou no servidor desde que você registrou.'
      : 'O servidor recusou este apontamento.';
  }

  private identity(): { userId: string; breweryId: string } | null {
    const user = this.auth.user();
    if (!user?.activeBrewery) {
      return null;
    }
    return { userId: user.userId, breweryId: user.activeBrewery.id };
  }
}
