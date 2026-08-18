/** Previsão de demanda (FCST-001). */

/**
 * `INSUFFICIENT` é ausência de previsão, e não confiança baixa.
 *
 * `HIGH` só a partir de doze meses, porque é a partir de um ciclo anual que a sazonalidade aparece no
 * dado em vez de ser adivinhada.
 */
export type ForecastConfidence = 'INSUFFICIENT' | 'LOW' | 'MODERATE' | 'HIGH';

export interface DemandForecast {
  productId: string;
  forMonth: string;
  /** Falso quando o histórico não bastou. A tela mostra a ausência, e não um zero. */
  hasNumbers: boolean;
  expectedUnits: number | null;
  lowerBound: number | null;
  upperBound: number | null;
  sampleMonths: number;
  method: string;
  /** Nulo quando não houve histórico para separar treino e teste. Nulo é honesto; zero mentiria. */
  meanAbsolutePercentageError: number | null;
  confidence: ForecastConfidence;
}

export const CONFIDENCE_LABELS: Record<ForecastConfidence, string> = {
  INSUFFICIENT: 'Sem previsão',
  LOW: 'Confiança baixa',
  MODERATE: 'Confiança média',
  HIGH: 'Confiança alta',
};

/**
 * O que cada nível autoriza — em português, e não como rótulo solto.
 *
 * O rótulo sozinho ainda deixa a leitura por conta de quem lê; a frase diz o que fazer com o número.
 */
export const CONFIDENCE_ADVICE: Record<ForecastConfidence, string> = {
  INSUFFICIENT: 'Histórico curto demais para prever. Não há número — e um número aqui viraria plano.',
  LOW: 'Não deve virar brassa sozinha. Olhe junto com quem conhece o mercado da casa.',
  MODERATE: 'Já diz alguma coisa, e ainda se mexe quando chega mês novo.',
  HIGH: 'Um ciclo anual de histórico: a sazonalidade está no dado, e não adivinhada.',
};

/**
 * A capacidade do próximo mês (DUV-FCST-001).
 *
 * `known: false` é "não sei" — e não zero. Zero diria que a cervejaria não produz nada, e alguém
 * planejaria em cima disso.
 */
export interface CapacityView {
  known: boolean;
  capacityLiters: number | null;
  demandLiters: number;
  /** Nulo quando a capacidade é desconhecida: responder "cabe" sem saber seria pior que não responder. */
  fits: boolean | null;
  /** Negativo quando falta — a falta é a informação que importa. */
  headroomLiters: number | null;
  utilizationPercent: number | null;
  /** Os tanques que entraram na conta, pelo código: uma capacidade explicada se confere. */
  tanks: string[];
}
