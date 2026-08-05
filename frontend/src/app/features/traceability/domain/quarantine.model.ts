/** Quarentena (FDS-002) — bloqueio de um nó e do que descende dele. */

import { LineageNode } from './genealogy.model';

export type QuarantineStatus = 'OPEN' | 'RELEASED';

export interface Quarantine {
  id: string;
  origin: LineageNode;
  reason: string;
  status: QuarantineStatus;
  openedAt: string;
  releasedAt: string | null;
  releaseJustification: string | null;
}

/**
 * Nó alcançado pelo bloqueio.
 *
 * <p>`suspected` é verdadeiro quando o caminho até ele passa por uma aresta de intenção — a reserva
 * de insumo, que diz qual lote foi separado para a OP e não qual foi ao moinho. Bloqueia igual, e
 * não afirma o mesmo: quem investiga precisa saber onde apertar primeiro.
 */
export interface Affected {
  node: LineageNode;
  suspected: boolean;
}

export interface QuarantineDetail {
  quarantine: Quarantine;
  /** Verdadeiro quando o corte de profundidade escondeu parte do alcance. */
  truncated: boolean;
  affected: Affected[];
}
