import { DestroyRef, Injectable, computed, inject, signal } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { finalize } from 'rxjs';
import {
  Direction,
  Genealogy,
  GenealogyQuery,
  LineageNode,
  NODE_ORDER,
  NodeType,
} from '../domain/genealogy.model';
import { GenealogyApi } from './genealogy.api';

interface GenealogyError {
  status?: number;
  code?: string;
  detail?: string;
  depth?: { maximum: number };
}

/** Uma coluna da cadeia: todos os nós de um tipo, na ordem em que a produção acontece. */
export interface LineageColumn {
  readonly type: NodeType;
  readonly nodes: readonly LineageNode[];
}

/** Estado da genealogia (TRC-001). */
@Injectable()
export class GenealogyStore {
  private readonly api = inject(GenealogyApi);
  private readonly destroyRef = inject(DestroyRef);

  readonly genealogy = signal<Genealogy | null>(null);
  readonly loading = signal(false);
  readonly error = signal<string | null>(null);
  readonly query = signal<GenealogyQuery | null>(null);

  /**
   * A cadeia em colunas, na ordem da produção — insumo à esquerda, envase à direita.
   *
   * <p>Colunas vazias saem: mostrar "Levedura" vazio para todo lote que não reaproveitou levedura
   * sugeriria uma lacuna onde não há nenhuma. Lacuna de verdade vem em `gaps`, com motivo.
   */
  readonly columns = computed<LineageColumn[]>(() => {
    const graph = this.genealogy();
    if (!graph) {
      return [];
    }
    return NODE_ORDER.map(type => ({
      type,
      nodes: graph.nodes.filter(node => node.type === type),
    })).filter(column => column.nodes.length > 0);
  });

  /** Arestas que são intenção. Ficam separadas porque exigem leitura diferente. */
  readonly intendedEdges = computed(() =>
    (this.genealogy()?.edges ?? []).filter(edge => edge.strength === 'INTENDED'),
  );

  readonly gaps = computed(() => this.genealogy()?.gaps ?? []);

  /** Nó que existe e não se liga a nada — diferente de nó inexistente, que é 404. */
  readonly isolated = computed(() => {
    const graph = this.genealogy();
    return graph !== null && graph.edges.length === 0;
  });

  load(query: GenealogyQuery): void {
    this.query.set(query);
    this.loading.set(true);
    this.error.set(null);
    this.api
      .genealogy(query)
      .pipe(takeUntilDestroyed(this.destroyRef), finalize(() => this.loading.set(false)))
      .subscribe({
        next: graph => this.genealogy.set(graph),
        error: (e: GenealogyError) => {
          this.genealogy.set(null);
          this.error.set(this.messageFor(e));
        },
      });
  }

  /** Recarrega mudando só o sentido — é a interação mais frequente da tela. */
  changeDirection(direction: Direction): void {
    const current = this.query();
    if (current && current.direction !== direction) {
      this.load({ ...current, direction });
    }
  }

  changeDepth(depth: number): void {
    const current = this.query();
    if (current && current.depth !== depth) {
      this.load({ ...current, depth });
    }
  }

  private messageFor(e: GenealogyError): string {
    if (e.code === 'unknown_node') {
      // Recusa de propósito: nó inexistente não é o mesmo que nó sem elos.
      return 'Este nó não existe nesta cervejaria. Confira de onde você chegou até aqui.';
    }
    if (e.code === 'depth_exceeded') {
      return `A profundidade máxima é de ${e.depth?.maximum ?? 10} saltos.`;
    }
    return 'Não foi possível carregar a genealogia.';
  }
}
