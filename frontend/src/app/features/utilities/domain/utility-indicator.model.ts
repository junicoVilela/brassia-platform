/** Consumo de água, energia e CO₂ por litro envasado (UTL-001). */

export type UtilityType = 'WATER' | 'ENERGY' | 'CO2' | 'CLEANING_PRODUCT';

export const UTILITY_LABELS: Record<UtilityType, string> = {
  WATER: 'Água',
  ENERGY: 'Energia',
  CO2: 'CO₂',
  CLEANING_PRODUCT: 'Produto de limpeza',
};

export const UTILITY_ICONS: Record<UtilityType, string> = {
  WATER: 'ri-drop-line',
  ENERGY: 'ri-flashlight-line',
  CO2: 'ri-cloud-line',
  CLEANING_PRODUCT: 'ri-flask-line',
};

/** Por qual parte da fábrica o número fala. Declarada por quem mede, não estimada aqui. */
export interface UtilityCoverage {
  what: string;
  reported: number;
  expected: number;
  complete: boolean;
}

export interface UtilityIndicator {
  type: UtilityType;
  unit: string;
  /** Lido em instrumento. */
  measured: number;
  /** Estimado por regra — não se soma ao medido num número só. */
  estimated: number;
  total: number;
  /** Nulo quando nada foi envasado no período: zero diria que a fábrica foi eficiente. */
  perLiter: number | null;
  /** Só a parte medida, por litro — é a que se leva a auditoria. */
  measuredPerLiter: number | null;
  /** Falso também quando ninguém declarou cobertura. */
  fullyMeasured: boolean;
  coverage: UtilityCoverage[];
  sources: string[];
}

export interface UtilityReport {
  from: string;
  to: string;
  /** Divisor do indicador: o volume das execuções de envase, não o dos planos. */
  packagedLiters: number;
  /** Uma entrada por utilidade que alguém mediu — as outras não vêm zeradas. */
  indicators: UtilityIndicator[];
}
