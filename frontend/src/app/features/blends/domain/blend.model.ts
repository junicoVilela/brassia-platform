/** O que a operação faz com o volume (BLD-001). */
export type BlendKind = 'MERGE' | 'SPLIT';

export type BlendStatus = 'SIMULATED' | 'APPROVED' | 'EXECUTED' | 'DISCARDED';

export interface BlendMovement {
  batchId: string;
  liters: number;
}

/**
 * Saída que ainda não é lote.
 *
 * A receita é DECLARADA por quem planeja, não herdada da origem predominante: uma união de 60% de IPA
 * com 40% de Stout não é "uma IPA", e o rótulo imprimiria o ABV e o estilo errados.
 */
export interface BlendResultPlan {
  recipeId: string;
  equipmentId: string;
  liters: number;
}

/** A saída planejada com o lote que ela produziu — `batchId` nulo enquanto não se executou. */
export interface BlendResult extends BlendResultPlan {
  seq: number;
  batchId: string | null;
}

export interface BlendOperation {
  id: string;
  kind: BlendKind;
  inputs: BlendMovement[];
  outputs: BlendMovement[];
  results: BlendResult[];
  inputLiters: number;
  outputLiters: number;
  declaredLossLiters: number;
  reason: string;
  status: BlendStatus;
  /** Se esta operação já pesa na genealogia — e portanto no recall. */
  contributesLineage: boolean;
  simulatedBy: string;
  simulatedAt: string;
  approvedBy: string | null;
  approvedAt: string | null;
  executedBy: string | null;
  executedAt: string | null;
}

export interface SimulateBlendRequest {
  kind: BlendKind;
  inputs: BlendMovement[];
  outputs: BlendMovement[];
  results: BlendResultPlan[];
  declaredLossLiters: number;
  reason: string;
}

export const KIND_LABELS: Record<BlendKind, string> = {
  MERGE: 'União',
  SPLIT: 'Divisão',
};

export const STATUS_LABELS: Record<BlendStatus, string> = {
  SIMULATED: 'Simulada',
  APPROVED: 'Aprovada',
  EXECUTED: 'Executada',
  DISCARDED: 'Descartada',
};

export const STATUS_CLASSES: Record<BlendStatus, string> = {
  SIMULATED: 'bg-secondary-subtle text-secondary-emphasis',
  APPROVED: 'bg-primary-subtle text-primary-emphasis',
  EXECUTED: 'bg-success-subtle text-success-emphasis',
  DISCARDED: 'bg-warning-subtle text-warning-emphasis',
};

/** Tolerância do balanço, em litros — o mesmo valor do domínio, para a tela avisar antes de enviar. */
export const TOLERANCE_LITERS = 0.1;
