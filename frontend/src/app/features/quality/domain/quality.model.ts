/** Tipos do plano de controle (QLT-001). */

export type ProcessStageCode = 'BREWING' | 'FERMENTATION' | 'MATURATION' | 'PACKAGING' | 'STORAGE';

export const STAGE_LABELS: Record<ProcessStageCode, string> = {
  BREWING: 'Brassagem',
  FERMENTATION: 'Fermentação',
  MATURATION: 'Maturação',
  PACKAGING: 'Envase',
  STORAGE: 'Estocagem',
};

export type SeverityCode = 'MINOR' | 'MAJOR' | 'CRITICAL';

export const SEVERITY_LABELS: Record<SeverityCode, string> = {
  MINOR: 'Leve',
  MAJOR: 'Grave',
  CRITICAL: 'Crítica',
};

export type FrequencyKindCode = 'PER_BATCH' | 'PER_HOURS' | 'PER_SHIFT' | 'PER_PACKAGING_RUN';

export const FREQUENCY_LABELS: Record<FrequencyKindCode, string> = {
  PER_BATCH: 'A cada lote',
  PER_HOURS: 'A cada N horas',
  PER_SHIFT: 'A cada turno',
  PER_PACKAGING_RUN: 'A cada envase',
};

export interface ControlPoint {
  id: string;
  parameter: string;
  min: number | null;
  max: number | null;
  target: number | null;
  unit: string;
  /** Texto pronto da faixa, formatado pelo backend — a tela não reimplementa a regra. */
  limits: string;
  frequencyKind: FrequencyKindCode;
  everyHours: number | null;
  frequency: string;
  action: string;
  severity: SeverityCode;
  severityLabel: string;
  critical: boolean;
}

export interface ControlPlan {
  id: string;
  code: string;
  name: string;
  recipeId: string | null;
  stage: ProcessStageCode;
  stageLabel: string;
  status: 'DRAFT' | 'PUBLISHED';
  version: number;
  points: ControlPoint[];
}

export interface Deviation {
  id: string;
  measurementId: string;
  planId: string;
  pointId: string;
  parameter: string;
  severity: SeverityCode;
  severityLabel: string;
  bound: 'BELOW_MIN' | 'ABOVE_MAX';
  limitValue: number;
  measuredValue: number;
  excess: number;
  unit: string;
  action: string;
  status: 'OPEN' | 'CLOSED';
  description: string;
  openedAt: string;
}

export interface Measurement {
  id: string;
  planId: string;
  planVersion: number;
  pointId: string;
  parameter: string;
  batchId: string | null;
  instrumentId: string | null;
  instrumentFitness: string | null;
  instrumentQuestionable: boolean;
  value: number;
  unit: string;
  withinSpec: boolean;
  note: string | null;
  measuredAt: string;
}

export interface MeasurementOutcome {
  measurementId: string;
  withinSpec: boolean;
  deviationId: string | null;
  deviation: Deviation | null;
}

export interface CreatePlanRequest {
  code: string;
  name: string;
  recipeId: string | null;
  stage: ProcessStageCode;
}

export interface AddPointRequest {
  parameter: string;
  min: number | null;
  max: number | null;
  target: number | null;
  unit: string;
  frequencyKind: FrequencyKindCode;
  everyHours: number | null;
  action: string;
  severity: SeverityCode;
  critical: boolean;
}

export interface RecordMeasurementRequest {
  planId: string;
  pointId: string;
  batchId: string | null;
  instrumentId: string | null;
  value: number;
  note: string | null;
  measuredAt: string | null;
}

/** Corpo do Problem Details quando o ponto crítico recusa o instrumento. */
export interface CriticalPointRefusal {
  parameter: string;
  instrument: string;
  fitness: string;
}
