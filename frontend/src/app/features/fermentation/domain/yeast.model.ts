export type YeastHarvestStatus = 'QUARANTINE' | 'APPROVED' | 'REJECTED' | 'USED';

export const YEAST_STATUS_LABELS: Record<YeastHarvestStatus, string> = {
  QUARANTINE: 'Em quarentena',
  APPROVED: 'Aprovada',
  REJECTED: 'Reprovada',
  USED: 'Usada',
};

export interface YeastHarvest {
  id: string;
  code: string;
  strainId: string;
  sourceBatchId: string;
  parentHarvestId: string | null;
  generation: number;
  harvestedAt: string;
  viabilityPercent: number;
  condition: string;
  storageLocation: string;
  storageTempC: number;
  status: YeastHarvestStatus;
  available: boolean;
  reviewNote: string | null;
  reviewedAt: string | null;
  pitchedBatchId: string | null;
  pitchedAt: string | null;
}

/** Limites de repitch da cervejaria (YST-002). */
export interface YeastPolicy {
  maxGeneration: number;
  maxAgeDays: number;
  minViabilityPercent: number;
}

export interface YeastRecommendationFactor {
  name: 'generation' | 'age' | 'viability';
  withinPolicy: boolean;
  explanation: string;
}

export interface YeastRecommendation {
  harvest: YeastHarvest;
  recommended: boolean;
  ageDays: number;
  factors: YeastRecommendationFactor[];
  blockers: string[];
}

export interface YeastReuse {
  policy: YeastPolicy;
  recommendations: YeastRecommendation[];
}

/** A geração nunca é enviada: o domínio a deriva da coleta-mãe. */
export interface CollectYeastRequest {
  code: string;
  strainId: string;
  sourceBatchId: string;
  parentHarvestId: string | null;
  harvestedAt: string;
  viabilityPercent: number;
  condition: string;
  storageLocation: string;
  storageTempC: number;
}
