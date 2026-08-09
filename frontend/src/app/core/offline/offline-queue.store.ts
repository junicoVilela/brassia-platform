import { Injectable, computed, signal } from '@angular/core';

/** O que um apontamento enfileirado carrega (PWA-002). */
export interface QueuedEntry {
  /**
   * Identidade do apontamento, gerada **no instante do registro** e não no envio.
   *
   * É o que torna a repetição reconhecível pelo servidor. Gerada no envio, ela seria diferente a cada
   * tentativa e criaria duas medições da mesma leitura — que é exatamente o que a fila existe para evitar.
   */
  clientRequestId: string;
  batchId: string;
  batchCode: string;
  /** O corpo exato que será enviado. Congelado: o apontamento é o que foi medido, não o que vale agora. */
  payload: Record<string, unknown>;
  /** Quando a pessoa registrou — não quando foi enviado. */
  recordedAt: string;
  attempts: number;
  status: QueueEntryStatus;
  /** Preenchido quando o servidor recusou por conflito de estado. */
  conflict: string | null;
}

/**
 * Situação de um item na fila.
 *
 * `CONFLICT` é terminal **do ponto de vista automático**: a fila para de tentar e devolve a decisão a quem
 * registrou. É o critério da história — conflito não sobrescreve em silêncio.
 */
export type QueueEntryStatus = 'PENDING' | 'CONFLICT';

interface StoredQueue {
  userId: string;
  breweryId: string;
  entries: QueuedEntry[];
}

const STORAGE_KEY = 'brassia.offline.queue';

/**
 * A fila de apontamentos feitos sem rede (PWA-002).
 *
 * <p><strong>A garantia desta fila é "ao menos uma vez", e isso é uma escolha.</strong> Ela reenvia até
 * receber confirmação, porque a alternativa — desistir na primeira falha — perderia o apontamento de quem
 * estava sem rede, que é a única razão de ela existir. "Exatamente uma vez" acontece do outro lado, pela
 * chave que viaja junto: o servidor reconhece o reenvio como o mesmo fato.
 *
 * <p><strong>Conflito não é falha de rede, e o tratamento é oposto.</strong> Falha de rede é transitória e
 * se resolve tentando de novo. Conflito é o servidor dizendo que o estado mudou — a etapa já foi concluída
 * por outra pessoa, o lote foi encerrado — e insistir só produziria o mesmo "não" mais quatro vezes. O item
 * sai do ciclo automático e **espera uma decisão humana**. Descartá-lo em silêncio seria perder o
 * apontamento; aplicá-lo à força seria sobrescrever o que outra pessoa fez.
 *
 * <p>A fila é carimbada com dono e cervejaria pelo mesmo motivo do roteiro offline: um aparelho de chão de
 * fábrica troca de turno, e apontamento de uma pessoa não pode ser enviado sob a sessão de outra.
 */
@Injectable({ providedIn: 'root' })
export class OfflineQueueStore {
  private readonly entriesState = signal<QueuedEntry[]>([]);

  readonly entries = this.entriesState.asReadonly();
  readonly pending = computed(() => this.entriesState().filter(e => e.status === 'PENDING'));
  readonly conflicts = computed(() => this.entriesState().filter(e => e.status === 'CONFLICT'));
  readonly hasPending = computed(() => this.pending().length > 0);
  readonly hasConflicts = computed(() => this.conflicts().length > 0);

  /**
   * Carrega a fila do aparelho, se ela for **desta pessoa e desta cervejaria**.
   *
   * <p>Fila de outro dono é apagada, não ignorada: deixá-la no disco guardaria o apontamento de alguém
   * num aparelho que trocou de mão.
   */
  load(userId: string, breweryId: string): void {
    const raw = globalThis.localStorage?.getItem(STORAGE_KEY);
    if (!raw) {
      this.entriesState.set([]);
      return;
    }
    let stored: StoredQueue;
    try {
      stored = JSON.parse(raw) as StoredQueue;
    } catch {
      this.clear();
      return;
    }
    if (stored.userId !== userId || stored.breweryId !== breweryId) {
      this.clear();
      return;
    }
    this.entriesState.set(stored.entries ?? []);
  }

  /** Enfileira um apontamento. A chave é gerada aqui, no registro. */
  enqueue(
    userId: string,
    breweryId: string,
    batchId: string,
    batchCode: string,
    payload: Record<string, unknown>,
    now: Date = new Date(),
  ): QueuedEntry {
    const entry: QueuedEntry = {
      clientRequestId: this.newKey(),
      batchId,
      batchCode,
      payload,
      recordedAt: now.toISOString(),
      attempts: 0,
      status: 'PENDING',
      conflict: null,
    };
    this.entriesState.update(entries => [...entries, entry]);
    this.persist(userId, breweryId);
    return entry;
  }

  /** Sai da fila: o servidor confirmou (novo ou repetido — os dois significam "está registrado"). */
  acknowledge(userId: string, breweryId: string, clientRequestId: string): void {
    this.entriesState.update(entries =>
      entries.filter(e => e.clientRequestId !== clientRequestId),
    );
    this.persist(userId, breweryId);
  }

  /** Falha transitória: conta a tentativa e mantém na fila para o próximo ciclo. */
  registerAttempt(userId: string, breweryId: string, clientRequestId: string): void {
    this.entriesState.update(entries =>
      entries.map(e =>
        e.clientRequestId === clientRequestId ? { ...e, attempts: e.attempts + 1 } : e,
      ),
    );
    this.persist(userId, breweryId);
  }

  /**
   * O servidor recusou por conflito de estado.
   *
   * <p>Sai do ciclo automático e espera decisão. **Nem descartado, nem aplicado à força** — o primeiro
   * perderia o apontamento, o segundo sobrescreveria o que outra pessoa fez.
   */
  markConflict(userId: string, breweryId: string, clientRequestId: string, reason: string): void {
    this.entriesState.update(entries =>
      entries.map(e =>
        e.clientRequestId === clientRequestId
          ? { ...e, status: 'CONFLICT' as const, conflict: reason, attempts: e.attempts + 1 }
          : e,
      ),
    );
    this.persist(userId, breweryId);
  }

  /** Quem registrou decidiu descartar o apontamento em conflito. É a única forma de ele sumir. */
  discard(userId: string, breweryId: string, clientRequestId: string): void {
    this.acknowledge(userId, breweryId, clientRequestId);
  }

  clear(): void {
    globalThis.localStorage?.removeItem(STORAGE_KEY);
    this.entriesState.set([]);
  }

  private persist(userId: string, breweryId: string): void {
    const stored: StoredQueue = { userId, breweryId, entries: this.entriesState() };
    try {
      globalThis.localStorage?.setItem(STORAGE_KEY, JSON.stringify(stored));
    } catch {
      // Cota estourada. O apontamento continua na memória desta sessão e será enviado se a rede voltar
      // antes de a aba fechar; não gravar é melhor que quebrar a tela de quem está registrando.
    }
  }

  private newKey(): string {
    return globalThis.crypto?.randomUUID?.() ?? `k-${Date.now()}-${Math.random().toString(36).slice(2)}`;
  }
}
