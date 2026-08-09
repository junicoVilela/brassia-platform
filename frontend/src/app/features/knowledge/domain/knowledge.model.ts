/** Um documento da base de conhecimento (RAG-001). Sem o texto: aqui se administra, não se lê manual. */
export interface KnowledgeDocument {
  id: string;
  type: DocumentType;
  code: string;
  title: string;
  version: number;
  effectiveFrom: string;
  effectiveTo: string | null;
  current: boolean;
  requiredPermission: string;
  equipmentId: string | null;
  sourceUri: string | null;
  chunks: number;
  indexedAt: string;
}

/**
 * Um trecho recuperado.
 *
 * `untrusted` vem sempre verdadeiro do servidor e não é decoração: o texto foi escrito por fabricante,
 * laboratório ou fornecedor e pode conter instrução endereçada ao modelo. A interface o mostra como
 * citação de terceiro, nunca como fala do sistema.
 */
export interface Evidence {
  documentId: string;
  code: string;
  title: string;
  type: DocumentType;
  version: number;
  effectiveOnDate: boolean;
  ordinal: number;
  text: string;
  score: number;
  untrusted: boolean;
}

export type DocumentType =
  | 'EQUIPMENT_MANUAL'
  | 'SAFETY_DATA_SHEET'
  | 'LAB_REPORT'
  | 'OPERATING_PROCEDURE'
  | 'TECHNICAL_NOTE';

/**
 * Como cada tipo se lê.
 *
 * O tipo muda a autoridade da citação: "o manual do fabricante diz" e "o laudo do lote diz" respondem
 * a mesma pergunta com pesos diferentes, e quem lê precisa saber qual dos dois respondeu.
 */
export const TYPE_LABELS: Record<DocumentType, string> = {
  EQUIPMENT_MANUAL: 'Manual de equipamento',
  SAFETY_DATA_SHEET: 'FISPQ',
  LAB_REPORT: 'Laudo',
  OPERATING_PROCEDURE: 'Procedimento',
  TECHNICAL_NOTE: 'Nota técnica',
};

export const TYPE_ICONS: Record<DocumentType, string> = {
  EQUIPMENT_MANUAL: 'ri-tools-line',
  SAFETY_DATA_SHEET: 'ri-alarm-warning-line',
  LAB_REPORT: 'ri-test-tube-line',
  OPERATING_PROCEDURE: 'ri-list-check-2',
  TECHNICAL_NOTE: 'ri-sticky-note-line',
};

export interface IndexRequest {
  type: DocumentType;
  code: string;
  title: string;
  effectiveFrom: string;
  requiredPermission: string;
  equipmentId: string | null;
  sourceUri: string | null;
  text: string;
}
