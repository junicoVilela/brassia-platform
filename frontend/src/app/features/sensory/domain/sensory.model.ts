/** Tipos da análise sensorial (SEN-001). */

export type SessionStatusCode = 'DRAFT' | 'OPEN' | 'CLOSED';

export type AttributeCode = 'APPEARANCE' | 'AROMA' | 'FLAVOR' | 'BODY' | 'OVERALL';

export const ATTRIBUTE_LABELS: Record<AttributeCode, string> = {
  APPEARANCE: 'Aparência',
  AROMA: 'Aroma',
  FLAVOR: 'Sabor',
  BODY: 'Corpo',
  OVERALL: 'Impressão global',
};

export const ATTRIBUTE_ORDER: AttributeCode[] = [
  'APPEARANCE',
  'AROMA',
  'FLAVOR',
  'BODY',
  'OVERALL',
];

/**
 * Amostra como a API a devolve.
 *
 * `batchId` vem **nulo enquanto a sessão não é encerrada** — a cegueira é garantida na resposta,
 * não na tela. Se dependesse do frontend, bastaria abrir o devtools para saber o que se está
 * provando.
 */
export interface SensorySample {
  id: string;
  blindCode: string;
  batchId: string | null;
  note: string | null;
}

export interface SensorySession {
  id: string;
  code: string;
  purpose: string;
  scheduledFor: string;
  status: SessionStatusCode;
  statusLabel: string;
  resultsAvailable: boolean;
  /** O único número público enquanto a sessão está aberta. */
  evaluationCount: number;
  samples: SensorySample[];
  openedAt: string | null;
  closedAt: string | null;
}

export interface SampleResult {
  sampleId: string;
  blindCode: string;
  batchId: string;
  evaluations: number;
  averages: Record<AttributeCode, number>;
  overallAverage: number;
  /** Diferença entre a maior e a menor nota global — painel disperso pede calibração. */
  spread: number;
  descriptors: string[];
}

/** Mesmo lote sob códigos diferentes: a diferença mede o painel, não a cerveja. */
export interface BatchConsistency {
  batchId: string;
  blindCodes: string[];
  difference: number;
}

export interface SessionResults {
  samples: SampleResult[];
  consistency: BatchConsistency[];
}

export interface CreateSessionRequest {
  code: string;
  purpose: string;
  scheduledFor: string;
}

export interface AddSampleRequest {
  batchId: string;
  note: string | null;
}

export interface SubmitEvaluationRequest {
  sampleId: string;
  scores: Record<string, number>;
  descriptors: string[];
  note: string | null;
}

/** Corpo do Problem Details quando o resultado é pedido antes do fechamento. */
export interface SessionRefusal {
  code: string;
  status: string;
}
