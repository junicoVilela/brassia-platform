/** Tipos do cadastro metrológico (MTR-001). */

export type InstrumentTypeCode =
  | 'THERMOMETER'
  | 'HYDROMETER'
  | 'PH_METER'
  | 'SCALE'
  | 'PRESSURE_GAUGE'
  | 'OXYGEN_METER'
  | 'FLOW_METER';

export const INSTRUMENT_TYPE_LABELS: Record<InstrumentTypeCode, string> = {
  THERMOMETER: 'Termômetro',
  HYDROMETER: 'Densímetro',
  PH_METER: 'pHmetro',
  SCALE: 'Balança',
  PRESSURE_GAUGE: 'Manômetro',
  OXYGEN_METER: 'Medidor de oxigênio',
  FLOW_METER: 'Medidor de vazão',
};

/**
 * Aptidão do instrumento — sempre derivada pelo backend na data da consulta, nunca calculada
 * aqui. Reimplementar a regra no frontend criaria uma segunda fonte de verdade que pode divergir
 * da que decide de fato.
 */
export type Fitness = 'FIT' | 'EXPIRED' | 'UNCALIBRATED' | 'REJECTED' | 'BLOCKED' | 'RETIRED';

export const FITNESS_LABELS: Record<Fitness, string> = {
  FIT: 'Apto',
  EXPIRED: 'Vencido',
  UNCALIBRATED: 'Sem calibração',
  REJECTED: 'Reprovado',
  BLOCKED: 'Bloqueado',
  RETIRED: 'Baixado',
};

export type CalibrationResultCode = 'APPROVED' | 'APPROVED_WITH_RESTRICTION' | 'REJECTED';

export const CALIBRATION_RESULT_LABELS: Record<CalibrationResultCode, string> = {
  APPROVED: 'Aprovado',
  APPROVED_WITH_RESTRICTION: 'Aprovado com restrição',
  REJECTED: 'Reprovado',
};

export interface Calibration {
  id: string;
  standardId: string;
  standardCode: string;
  performedOn: string;
  dueOn: string;
  performedBy: string;
  certificateNumber: string;
  result: CalibrationResultCode;
  resultLabel: string;
  maxDeviation: number;
  restriction: string | null;
  note: string | null;
}

export interface Instrument {
  id: string;
  code: string;
  name: string;
  type: InstrumentTypeCode;
  typeLabel: string;
  rangeMin: number;
  rangeMax: number;
  resolution: number;
  accuracy: number;
  unit: string;
  location: string;
  state: 'ACTIVE' | 'BLOCKED' | 'RETIRED';
  blockReason: string | null;
  criticalUse: boolean;
  fitness: Fitness;
  fitForCriticalUse: boolean;
  calibrationDueOn: string | null;
  lastCalibration: Calibration | null;
}

export interface CalibrationStandard {
  id: string;
  code: string;
  description: string;
  certificateNumber: string;
  issuer: string;
  traceability: string;
  validUntil: string;
  expired: boolean;
}

export interface RegisterInstrumentRequest {
  code: string;
  name: string;
  type: InstrumentTypeCode;
  rangeMin: number;
  rangeMax: number;
  resolution: number;
  accuracy: number;
  unit: string;
  location: string;
}

export interface RecordCalibrationRequest {
  standardId: string;
  performedOn: string;
  dueOn: string;
  performedBy: string;
  certificateNumber: string;
  result: CalibrationResultCode;
  maxDeviation: number;
  restriction: string | null;
  note: string | null;
}

export interface RegisterStandardRequest {
  code: string;
  description: string;
  certificateNumber: string;
  issuer: string;
  traceability: string;
  validUntil: string;
}

/** Corpo do Problem Details quando o instrumento não está apto para o que se pediu. */
export interface InstrumentNotFit {
  code: string;
  fitness: Fitness;
  calibrationDueOn: string;
}

/** Corpo do Problem Details quando o padrão já tinha vencido na data da calibração. */
export interface StandardExpired {
  code: string;
  validUntil: string;
  performedOn: string;
}
