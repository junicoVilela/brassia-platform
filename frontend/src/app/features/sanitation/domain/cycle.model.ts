export type CycleStatus = 'IN_PROGRESS' | 'INTERRUPTED' | 'COMPLETED';
export type CycleStepStatus = 'PENDING' | 'DONE';

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
