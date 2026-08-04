/**
 * Parâmetros por cervejaria (PRM-001).
 *
 * <p>Cada política vive no módulo dono do conceito; esta tela apenas as reúne. Por isso o modelo
 * aqui é a composição de cinco respostas independentes, não um objeto único vindo do backend.
 */

/** Validade da liberação de CIP. Ausente significa que não expira por tempo. */
export interface CleaningPolicy {
  validityHours: number | null;
  expiresByTime: boolean;
}

/** Periodicidade de requalificação de cilindro. */
export interface GasPolicy {
  requalificationMonths: number | null;
  derivesDueDate: boolean;
}

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

/** Periodicidade por tipo; tipo ausente do mapa não deriva vencimento. */
export interface CalibrationPolicy {
  monthsByType: Partial<Record<InstrumentTypeCode, number>>;
}

export type SeverityCode = 'MINOR' | 'MAJOR' | 'CRITICAL';

export const SEVERITY_LABELS: Record<SeverityCode, string> = {
  MINOR: 'Leve',
  MAJOR: 'Grave',
  CRITICAL: 'Crítica',
};

export interface CapaDeadlines {
  containmentDays: number;
  investigationDays: number;
  verificationDays: number;
}

/** Prazos em dias da abertura; severidade ausente exige prazo informado na NC. */
export interface CapaPolicy {
  bySeverity: Partial<Record<SeverityCode, CapaDeadlines>>;
}

/** Escala da ficha sensorial; congelada em cada sessão no momento da criação. */
export interface SensoryPolicy {
  maxScore: number;
  appliesToNewSessionsOnly: boolean;
}

/** As cinco políticas reunidas para a tela. */
export interface Parameters {
  cleaning: CleaningPolicy;
  gas: GasPolicy;
  calibration: CalibrationPolicy;
  capa: CapaPolicy;
  sensory: SensoryPolicy;
}
