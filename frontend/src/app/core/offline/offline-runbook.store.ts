import { Injectable, computed, signal } from '@angular/core';

/**
 * Um roteiro guardado para uso sem rede (PWA-001).
 *
 * <p>Só o que se lê no chão de fábrica: a OP, o lote e as etapas. **Nada de custo, fornecedor, preço,
 * auditoria ou dado de pessoa** — não porque a tela não os mostraria, mas porque o que não é gravado não
 * pode vazar de um aparelho perdido.
 */
export interface OfflineRunbook {
  batchId: string;
  code: string;
  recipeName: string;
  recipeVersion: number;
  volumeLiters: number;
  status: string;
  startedAt: string;
  steps: OfflineRunbookStep[];
}

export interface OfflineRunbookStep {
  id: string;
  sequence: number;
  type: string;
  label: string;
  status: string;
  completedAt: string | null;
}

/**
 * O envelope gravado: o roteiro mais **de quem** e **de quando** ele é.
 *
 * <p>Os três campos de identidade não são metadados de conveniência — são a proteção inteira. Ver
 * {@link OfflineRunbookStore#read}.
 */
interface StoredEnvelope {
  userId: string;
  breweryId: string;
  savedAt: string;
  runbook: OfflineRunbook;
}

const STORAGE_PREFIX = 'brassia.offline.runbook.';

/**
 * Guarda roteiros para leitura sem rede (PWA-001).
 *
 * <p><strong>Por que isto existe em vez de um `dataGroup` do service worker.</strong> O ngsw sabe cachear
 * respostas de API por padrão de URL, e isso resolveria "ler offline" em três linhas de configuração. O
 * problema é o que ele faz junto: cacheia <em>tudo</em> que casa com o padrão, de forma invisível, num
 * armazenamento que sobrevive ao logout e é legível por quem usar o aparelho depois. Um tablet de chão de
 * fábrica é compartilhado por turno; um cache que guarda "o que passou pela API" acaba guardando o custo
 * do lote e a auditoria porque alguém abriu essas telas uma vez.
 *
 * <p>Aqui o que fica gravado é <strong>escolhido, nomeado e datado</strong>: só o roteiro, só quando
 * alguém pede, e sempre com a identidade de quem pediu.
 *
 * <p><strong>Três defesas, e a ordem importa.</strong>
 *
 * <ol>
 *   <li><strong>Dono.</strong> Ler um roteiro salvo por outro usuário é recusado — e o registro é apagado
 *       na hora. É o caso do tablet que troca de turno: o operador seguinte não vê o que o anterior baixou.
 *   <li><strong>Cervejaria.</strong> Mesmo usuário, cervejaria diferente, mesma recusa. Trocar de
 *       cervejaria não pode deixar o roteiro da anterior visível.
 *   <li><strong>Validade.</strong> Um roteiro velho descreve um lote que já mudou. Depois do prazo ele é
 *       apagado em vez de exibido, porque um roteiro desatualizado apresentado como atual é pior que
 *       nenhum: leva alguém a executar a etapa errada com confiança.
 * </ol>
 *
 * <p>Nas três, a resposta é <strong>apagar</strong>, não só esconder. Esconder deixaria o dado no disco.
 */
@Injectable({ providedIn: 'root' })
export class OfflineRunbookStore {
  /**
   * Por quanto tempo um roteiro salvo continua utilizável.
   *
   * <p>Doze horas cobrem um turno e a leitura do turno seguinte. Mais que isso e o roteiro passa a
   * descrever um lote que avançou sem que o aparelho soubesse.
   */
  private static readonly MAX_AGE_MS = 12 * 60 * 60 * 1000;

  private readonly savedIds = signal<string[]>(this.readIndex());

  /** Os lotes com roteiro disponível offline, para a tela marcar quais são. */
  readonly available = this.savedIds.asReadonly();
  readonly count = computed(() => this.savedIds().length);

  isAvailable(batchId: string): boolean {
    return this.savedIds().includes(batchId);
  }

  /** Grava o roteiro carimbado com quem salvou, para qual cervejaria e quando. */
  save(userId: string, breweryId: string, runbook: OfflineRunbook, now: Date = new Date()): void {
    const envelope: StoredEnvelope = {
      userId,
      breweryId,
      savedAt: now.toISOString(),
      runbook,
    };
    try {
      globalThis.localStorage?.setItem(this.keyOf(runbook.batchId), JSON.stringify(envelope));
    } catch {
      // Cota estourada ou armazenamento bloqueado pelo navegador. Não há o que fazer além de não
      // gravar — e falhar em silêncio aqui é melhor que quebrar a tela, porque a leitura online segue
      // funcionando. A tela mostra o que está disponível a partir do índice, que não terá este lote.
      return;
    }
    this.refreshIndex();
  }

  /**
   * Lê o roteiro salvo, se ele for **desta pessoa, desta cervejaria e recente**.
   *
   * <p>Devolve `null` em qualquer outra situação, sempre apagando o registro antes.
   */
  read(
    userId: string,
    breweryId: string,
    batchId: string,
    now: Date = new Date(),
  ): OfflineRunbook | null {
    const raw = globalThis.localStorage?.getItem(this.keyOf(batchId));
    if (!raw) {
      return null;
    }

    let envelope: StoredEnvelope;
    try {
      envelope = JSON.parse(raw) as StoredEnvelope;
    } catch {
      // Conteúdo corrompido ou de uma versão anterior do formato. Apagar é o certo: não há como saber de
      // quem ele é, e o que não se sabe de quem é não se mostra.
      this.discard(batchId);
      return null;
    }

    if (envelope.userId !== userId || envelope.breweryId !== breweryId) {
      this.discard(batchId);
      return null;
    }

    const age = now.getTime() - new Date(envelope.savedAt).getTime();
    if (!Number.isFinite(age) || age > OfflineRunbookStore.MAX_AGE_MS) {
      this.discard(batchId);
      return null;
    }

    return envelope.runbook;
  }

  /** Quando o roteiro salvo foi capturado — a tela precisa dizer isso a quem lê sem rede. */
  savedAt(batchId: string): Date | null {
    const raw = globalThis.localStorage?.getItem(this.keyOf(batchId));
    if (!raw) {
      return null;
    }
    try {
      const saved = new Date((JSON.parse(raw) as StoredEnvelope).savedAt);
      return Number.isNaN(saved.getTime()) ? null : saved;
    } catch {
      return null;
    }
  }

  discard(batchId: string): void {
    globalThis.localStorage?.removeItem(this.keyOf(batchId));
    this.refreshIndex();
  }

  /**
   * Apaga tudo.
   *
   * <p>Chamado no logout e na troca de cervejaria. As verificações de dono e cervejaria já impediriam a
   * leitura, mas impedir a leitura não é o suficiente: o dado continuaria no disco do aparelho, e um
   * aparelho de chão de fábrica se perde. Sair da conta tem que significar que não sobrou nada.
   */
  clearAll(): void {
    const storage = globalThis.localStorage;
    if (!storage) {
      return;
    }
    for (const key of this.storedKeys()) {
      storage.removeItem(key);
    }
    this.savedIds.set([]);
  }

  private keyOf(batchId: string): string {
    return STORAGE_PREFIX + batchId;
  }

  private storedKeys(): string[] {
    const storage = globalThis.localStorage;
    if (!storage) {
      return [];
    }
    return Object.keys(storage).filter(key => key.startsWith(STORAGE_PREFIX));
  }

  private readIndex(): string[] {
    return this.storedKeys().map(key => key.slice(STORAGE_PREFIX.length));
  }

  private refreshIndex(): void {
    this.savedIds.set(this.readIndex());
  }
}
