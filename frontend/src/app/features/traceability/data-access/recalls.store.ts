import { DestroyRef, Injectable, computed, inject, signal } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { Observable, finalize } from 'rxjs';
import { ToastService } from '../../../core/notifications/toast.service';
import { NODE_ORDER, NodeType } from '../domain/genealogy.model';
import { Recall, RecallDossier } from '../domain/recall.model';
import { RecallsApi } from './recalls.api';

interface RecallError {
  status?: number;
  code?: string;
  detail?: string;
  pending?: number;
}

/**
 * Estado dos recalls (FDS-003).
 *
 * <p>O dossiê é sempre relido do servidor: metade dele é derivada do grafo, e uma cópia local
 * envelheceria exatamente como a cópia no banco envelheceria — que é o motivo de o backend não a
 * manter.
 */
@Injectable()
export class RecallsStore {
  private readonly api = inject(RecallsApi);
  private readonly toast = inject(ToastService);
  private readonly destroyRef = inject(DestroyRef);

  readonly recalls = signal<Recall[]>([]);
  readonly loading = signal(false);
  readonly error = signal<string | null>(null);

  readonly dossier = signal<RecallDossier | null>(null);
  readonly dossierLoading = signal(false);
  readonly saving = signal<string | null>(null);
  readonly actionError = signal<string | null>(null);

  readonly openCount = computed(
    () => this.recalls().filter(recall => recall.status === 'OPEN').length,
  );

  /** Destinos descobertos depois da abertura — o lote saiu depois, e ninguém os avisou ainda. */
  readonly newDestinations = computed(() => this.dossier()?.newDestinations ?? []);

  /**
   * O escopo do recall agrupado por tipo de nó, na ordem da cadeia.
   *
   * <p><strong>Existe porque os vasilhames não apareciam em lugar nenhum.</strong> A API devolve o
   * escopo com os nós `CONTAINER` desde a CON-002, e a tela mostrava só os destinos — que vêm de
   * expedição. Numa casa que opera retornável, o keg no cliente é exatamente o que o recall precisa
   * recolher, e ele ficava invisível a um campo de distância (DEB-TRC-003).
   *
   * <p>Agrupar por tipo, e não listar corrido, é o que torna a leitura acionável: "recolher 12
   * vasilhames" e "avisar 3 distribuidoras" são duas tarefas, de duas pessoas, em dois lugares.
   */
  readonly scopeByType = computed(() => {
    const scope = this.dossier()?.scope ?? [];
    return NODE_ORDER.map(type => ({
      type,
      nodes: scope.filter(affected => affected.node.type === type),
    })).filter(group => group.nodes.length > 0);
  });

  /** Quantos vasilhames o recall alcança — o número que a operação de rua vai procurar. */
  readonly containersInScope = computed(
    () => (this.dossier()?.scope ?? []).filter(a => a.node.type === 'CONTAINER').length,
  );

  load(): void {
    this.loading.set(true);
    this.error.set(null);
    this.api
      .list()
      .pipe(takeUntilDestroyed(this.destroyRef), finalize(() => this.loading.set(false)))
      .subscribe({
        next: recalls => this.recalls.set(recalls),
        error: () => this.error.set('Não foi possível carregar os recalls.'),
      });
  }

  /** Abre o dossiê; o mesmo id duas vezes fecha, porque a linha funciona como acordeão. */
  select(id: string): void {
    if (this.dossier()?.recall.id === id) {
      this.dossier.set(null);
      return;
    }
    this.dossier.set(null);
    this.reloadDossier(id);
  }

  open(nodeType: NodeType, nodeId: string, reason: string): void {
    this.run('open', this.api.open(nodeType, nodeId, reason), 'Recall aberto.', null);
  }

  notify(recallId: string, notificationId: string, channel: string, note: string | null): void {
    this.run(`notify:${notificationId}`, this.api.notify(recallId, notificationId, channel, note),
      'Comunicação registrada.', recallId);
  }

  close(recallId: string, summary: string): void {
    this.run(`close:${recallId}`, this.api.close(recallId, summary), 'Recall encerrado.', recallId);
  }

  private run<T>(key: string, call: Observable<T>, message: string, reload: string | null): void {
    this.saving.set(key);
    this.actionError.set(null);
    call.pipe(takeUntilDestroyed(this.destroyRef), finalize(() => this.saving.set(null))).subscribe({
      next: () => {
        this.toast.success(message);
        this.load();
        if (reload) {
          this.reloadDossier(reload);
        }
      },
      error: (e: RecallError) => this.actionError.set(this.messageFor(e)),
    });
  }

  private reloadDossier(id: string): void {
    this.dossierLoading.set(true);
    this.api
      .dossier(id)
      .pipe(takeUntilDestroyed(this.destroyRef), finalize(() => this.dossierLoading.set(false)))
      .subscribe({
        next: dossier => this.dossier.set(dossier),
        error: () => this.actionError.set('Não foi possível carregar o dossiê.'),
      });
  }

  private messageFor(e: RecallError): string {
    if (e.code === 'recall_has_pending_notifications') {
      return `Há ${e.pending ?? 0} destino(s) sem comunicação registrada. Encerrar agora diria que a `
        + 'operação terminou com cliente sem aviso.';
    }
    if (e.code === 'unknown_node') {
      return 'Este nó não existe nesta cervejaria.';
    }
    if (e.code === 'unknown_recall') {
      return 'Este recall não existe mais.';
    }
    if (e.status === 403) {
      return 'Abrir, comunicar e encerrar recall exigem alçada própria.';
    }
    return e.detail ?? 'Não foi possível concluir a operação.';
  }
}
