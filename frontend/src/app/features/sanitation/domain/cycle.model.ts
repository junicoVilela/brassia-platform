export type CycleStatus = 'IN_PROGRESS' | 'INTERRUPTED' | 'COMPLETED' | 'RELEASED' | 'REJECTED';
export type CycleStepStatus = 'PENDING' | 'DONE';

export interface Verification {
  rinseOk: boolean;
  visualOk: boolean;
  atpRlu: number;
  atpThreshold: number;
  atpOk: boolean;
  microOk: boolean;
  passed: boolean;
  verifiedAt: string;
}

export interface VerificationRequest {
  rinseOk: boolean;
  visualOk: boolean;
  atpRlu: number;
  atpThreshold: number;
  microOk: boolean;
}

export interface Consumption {
  waterLiters: number;
  energyKwh: number;
  productKg: number;
  recordedAt: string;
}

export interface ConsumptionRequest {
  waterLiters: number;
  energyKwh: number;
  productKg: number;
}

export interface ConsumptionSummary {
  procedureCode: string;
  cycleCount: number;
  avgWaterLiters: number | null;
  minWaterLiters: number | null;
  maxWaterLiters: number | null;
  avgEnergyKwh: number | null;
  minEnergyKwh: number | null;
  maxEnergyKwh: number | null;
  avgProductKg: number | null;
  minProductKg: number | null;
  maxProductKg: number | null;
}

export interface CycleStep {
  sequence: number;
  method: string;
  product: string | null;
  concentrationMinPct: number | null;
  concentrationMaxPct: number | null;
  tempMinC: number | null;
  tempMaxC: number | null;
  timeMinutes: number | null;
  prohibition: string | null;
  evidenceRequired: boolean;
  status: CycleStepStatus;
  measuredConcentrationPct: number | null;
  measuredTempC: number | null;
  measuredTimeMinutes: number | null;
  flowActual: string | null;
  evidence: string | null;
  outOfOrderReason: string | null;
  overridden: boolean;
  overrideReason: string | null;
  executedAt: string | null;
}

export interface CleaningCycle {
  id: string;
  procedureId: string;
  procedureCode: string;
  procedureVersion: number;
  equipmentId: string;
  status: CycleStatus;
  interruptReason: string | null;
  startedAt: string;
  endedAt: string | null;
  decidedAt: string | null;
  verification: Verification | null;
  consumption: Consumption | null;
  steps: CycleStep[];
}

export interface StartCycleRequest {
  procedureCode: string;
  equipmentId: string;
}

export interface RecordStepRequest {
  sequence: number;
  measuredConcentrationPct?: number | null;
  measuredTempC?: number | null;
  measuredTimeMinutes?: number | null;
  flow?: string | null;
  evidence?: string | null;
  outOfOrderReason?: string | null;
  override?: boolean;
  overrideReason?: string | null;
}
