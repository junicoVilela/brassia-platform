/** Quanto se pode apoiar numa estimativa (DTW-001). */
export type Confidence = 'INSUFFICIENT' | 'LOW' | 'MODERATE' | 'HIGH';

export type ProfileMetric = 'VOLUME_YIELD_PERCENT' | 'TRANSFER_LOSS_LITERS';

/**
 * Uma estimativa aprendida.
 *
 * A média **nunca** aparece sozinha na tela: vem sempre com o tamanho da amostra, a faixa e o rótulo.
 * "Rendimento de 92%" pode vir de trinta lotes agrupados ou de dois — um de 84 e um de 100 —, e as duas
 * coisas significam o oposto para quem vai planejar a próxima receita.
 */
export interface ProfileEstimate {
  metric: ProfileMetric;
  label: string;
  mean: number | null;
  standardDeviation: number | null;
  lowerBound: number | null;
  upperBound: number | null;
  sampleSize: number;
  confidence: Confidence;
  usable: boolean;
}

export interface LearnedProfile {
  id: string;
  recipeId: string;
  version: number;
  estimates: ProfileEstimate[];
  /** Os lotes lidos. É o que torna o número reproduzível — e a exclusão de um lote, visível. */
  observedBatchIds: string[];
  computedAt: string;
  hasAnyUsableEstimate: boolean;
}

export interface ComputeProfileRequest {
  recipeId: string;
  batchIds: string[];
}

/**
 * Como cada confiança se lê.
 *
 * O texto explica **o que fazer**, não só o nível: um rótulo que diz apenas "baixa" deixa quem lê decidir
 * sozinho o que isso significa, e a leitura otimista é a mais provável.
 */
export const CONFIDENCE_LABELS: Record<Confidence, string> = {
  INSUFFICIENT: 'Sem estimativa',
  LOW: 'Confiança baixa',
  MODERATE: 'Confiança média',
  HIGH: 'Confiança alta',
};

export const CONFIDENCE_HINTS: Record<Confidence, string> = {
  INSUFFICIENT: 'Menos de dois lotes observados — não há o que estimar.',
  LOW: 'Poucos lotes. Olhe junto com quem conhece o equipamento; não use sozinho para planejar.',
  MODERATE: 'Já diz alguma coisa, mas ainda se mexe quando chega lote novo.',
  HIGH: 'A média parou de oscilar a cada lote novo.',
};

export const CONFIDENCE_CLASSES: Record<Confidence, string> = {
  INSUFFICIENT: 'bg-secondary-subtle text-secondary-emphasis',
  LOW: 'bg-danger-subtle text-danger-emphasis',
  MODERATE: 'bg-warning-subtle text-warning-emphasis',
  HIGH: 'bg-success-subtle text-success-emphasis',
};
