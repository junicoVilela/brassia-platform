/** Genealogia de um nó (TRC-001) — o grafo derivado que a API devolve. */

export type NodeType =
  | 'STOCK_LOT'
  | 'BREW_ORDER'
  | 'BATCH'
  | 'YEAST_HARVEST'
  | 'PACKAGING_PLAN'
  | 'PACKAGING_RUN'
  | 'FINISHED_LOT'
  | 'SHIPMENT'
  | 'CONTAINER';

/** Ordem da cadeia produtiva; é ela que dá a leitura da esquerda para a direita na tela. */
export const NODE_ORDER: readonly NodeType[] = [
  'STOCK_LOT',
  'BREW_ORDER',
  'BATCH',
  'YEAST_HARVEST',
  'PACKAGING_PLAN',
  'PACKAGING_RUN',
  'FINISHED_LOT',
  // As duas pontas de saída, e elas são paralelas: o produto sai por expedição (a caixa que se
  // vende) ou dentro de um vasilhame retornável (o keg que volta). Um recall precisa das duas.
  'SHIPMENT',
  'CONTAINER',
];

export const NODE_LABELS: Record<NodeType, string> = {
  STOCK_LOT: 'Insumo',
  BREW_ORDER: 'Ordem',
  BATCH: 'Lote',
  YEAST_HARVEST: 'Levedura',
  PACKAGING_PLAN: 'Plano de envase',
  PACKAGING_RUN: 'Envase',
  FINISHED_LOT: 'Produto acabado',
  SHIPMENT: 'Expedição',
  CONTAINER: 'Vasilhame',
};

export const NODE_ICONS: Record<NodeType, string> = {
  STOCK_LOT: 'ri-archive-line',
  BREW_ORDER: 'ri-file-list-3-line',
  BATCH: 'ri-flask-line',
  YEAST_HARVEST: 'ri-bubble-chart-line',
  PACKAGING_PLAN: 'ri-inbox-line',
  PACKAGING_RUN: 'ri-inbox-archive-line',
  FINISHED_LOT: 'ri-barcode-line',
  SHIPMENT: 'ri-truck-line',
  CONTAINER: 'ri-goblet-line',
};

export type Direction = 'BACKWARD' | 'FORWARD' | 'BOTH';

/** CONFIRMED é fato registrado; INTENDED é intenção — a reserva, não o consumo. */
export type EdgeStrength = 'CONFIRMED' | 'INTENDED';

export interface LineageNode {
  type: NodeType;
  id: string;
  label: string | null;
}

export interface LineageEdge {
  from: LineageNode;
  to: LineageNode;
  kind: string;
  strength: EdgeStrength;
  recordedAt: string | null;
}

/** Elo que deveria existir e não existe, com o motivo. */
export interface LineageGap {
  from: LineageNode;
  expectedLink: string;
  reason: string;
}

export interface Genealogy {
  root: LineageNode;
  direction: Direction;
  depth: number;
  /** Verdadeiro quando o corte de profundidade escondeu parte do grafo. */
  truncated: boolean;
  nodes: LineageNode[];
  edges: LineageEdge[];
  gaps: LineageGap[];
}

/** Consulta: de onde partir, para que lado e até onde. */
export interface GenealogyQuery {
  nodeType: NodeType;
  nodeId: string;
  direction: Direction;
  depth: number;
}
