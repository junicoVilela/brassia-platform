export type PackagingPlanStatus = 'PLANNED' | 'RESERVED' | 'EXECUTED' | 'CANCELLED';

export const PACKAGING_STATUS_LABELS: Record<PackagingPlanStatus, string> = {
  PLANNED: 'Planejado',
  RESERVED: 'Reservado',
  EXECUTED: 'Envasado',
  CANCELLED: 'Cancelado',
};

export type ChecklistItemCode = 'CONTAINER_INSPECTED' | 'SEAL_TEST' | 'GAS_SUPPLY';

export const CHECKLIST_LABELS: Record<ChecklistItemCode, string> = {
  CONTAINER_INSPECTED: 'Embalagem inspecionada',
  SEAL_TEST: 'Vedação testada',
  GAS_SUPPLY: 'Gás conectado e conferido',
};

export interface PackagingChecklistItem {
  item: ChecklistItemCode;
  confirmed: boolean;
  confirmedBy: string | null;
  confirmedAt: string | null;
}

export interface PackagingPlan {
  id: string;
  code: string;
  batchId: string;
  containerId: string;
  containerVolumeMl: number;
  plannedUnits: number;
  plannedVolumeLiters: number;
  lineEquipmentId: string;
  plannedStart: string;
  plannedEnd: string;
  status: PackagingPlanStatus;
  checklist: PackagingChecklistItem[];
  checklistComplete: boolean;
  reservedAt: string | null;
  cancelReason: string | null;
}

/** O volume nunca é enviado: o domínio o deriva de unidades × volume da embalagem. */
export interface PlanPackagingRequest {
  code: string;
  batchId: string;
  containerId: string;
  plannedUnits: number;
  lineEquipmentId: string;
  plannedStart: string;
  plannedEnd: string;
}

/** Motivo estável da recusa de reserva, como o backend o publica. */
export type BlockerCode =
  | 'checklist_pending'
  | 'line_unknown'
  | 'line_inactive'
  | 'line_under_maintenance'
  | 'line_conflict'
  | 'line_not_clean';

export interface PackagingBlocker {
  code: BlockerCode;
  message: string;
}

export interface PackagingShortfall {
  containerId: string;
  requested: number;
  available: number;
  unit: string;
}

export interface ReserveResult {
  planId: string;
  reservedUnits: number;
  unit: string;
}

export type CarbonationMethod = 'PRIMING' | 'FORCED';

export const CARBONATION_METHOD_LABELS: Record<CarbonationMethod, string> = {
  PRIMING: 'Priming (açúcar na embalagem)',
  FORCED: 'Carbonatação forçada (pressão)',
};

export type PrimingSugarCode = 'SUCROSE' | 'DEXTROSE_MONOHYDRATE' | 'DRY_MALT_EXTRACT';

export const PRIMING_SUGAR_LABELS: Record<PrimingSugarCode, string> = {
  SUCROSE: 'Sacarose (açúcar refinado)',
  DEXTROSE_MONOHYDRATE: 'Dextrose mono-hidratada',
  DRY_MALT_EXTRACT: 'Extrato seco de malte (rendimento estimado)',
};

/** Recomendação de carbonatação: entradas, método, resultado e alertas — nada gravado. */
export interface CarbonationRecommendation {
  method: CarbonationMethod;
  targetVolumes: number;
  referenceTempC: number;
  residualVolumes: number;
  missingVolumes: number;
  beerVolumeLiters: number;
  primingSugar: PrimingSugarCode | null;
  primingSugarGrams: number | null;
  pressureBar: number | null;
  calculationMethod: string;
  calculatorVersion: string;
  assumptions: string[];
  alerts: string[];
}

/** Decisão confirmada: o que a prévia mostrava, mais quem confirmou e quando. */
export interface Carbonation {
  method: CarbonationMethod;
  targetVolumes: number;
  referenceTempC: number;
  residualVolumes: number;
  missingVolumes: number;
  primingSugar: PrimingSugarCode | null;
  primingSugarGrams: number | null;
  pressureBar: number | null;
  calculationMethod: string;
  calculatorVersion: string;
  alerts: string[];
  confirmedBy: string;
  confirmedAt: string;
}

export interface CarbonationInput {
  method: CarbonationMethod;
  targetVolumes: number;
  referenceTempC: number;
  primingSugar: PrimingSugarCode | null;
}

export interface OverCarbonation {
  targetVolumes: number;
  residualVolumes: number;
}

/** Execução do envase (PKG-003). A perda é derivada pelo backend, nunca digitada. */
export interface PackagingRun {
  id: string;
  batchId: string;
  inputVolumeLiters: number;
  producedUnits: number;
  rejectedUnits: number;
  packagedVolumeLiters: number;
  rejectedVolumeLiters: number;
  lossesLiters: number;
  lossPercent: number;
  /** Boas + rejeitadas: uma lata cheia e descartada é uma lata gasta. */
  containersConsumed: number;
  note: string | null;
  executedAt: string;
  executedBy: string;
}

export interface ExecutePackagingRequest {
  inputVolumeLiters: number;
  producedUnits: number;
  rejectedUnits: number;
  note: string | null;
}

export interface VolumeBalance {
  inputVolumeLiters: number;
  packagedVolumeLiters: number;
  rejectedVolumeLiters: number;
  shortfallLiters: number;
}

export interface BatchVolumeExceeded {
  batchVolumeLiters: number;
  alreadyPackagedLiters: number;
  remainingLiters: number;
  requestedLiters: number;
}
