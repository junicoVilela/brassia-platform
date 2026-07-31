export type YeastHarvestStatus = 'QUARANTINE' | 'APPROVED' | 'REJECTED';

export const YEAST_STATUS_LABELS: Record<YeastHarvestStatus, string> = {
  QUARANTINE: 'Em quarentena',
  APPROVED: 'Aprovada',
  REJECTED: 'Reprovada',
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
