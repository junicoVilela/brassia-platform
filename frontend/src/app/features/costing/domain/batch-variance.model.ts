/** Planejado versus real do lote (CST-002): a explicação da diferença, sempre derivada. */

export type VolumeKind = 'YIELD' | 'LOSS';

export const VOLUME_KIND_LABELS: Record<VolumeKind, string> = {
  YIELD: 'Rendimento',
  LOSS: 'Perda',
};

export interface MaterialVariance {
  ingredientId: string;
  name: string;
  /** Unidade canônica: KG, L ou UNIT. */
  unit: string;
  /** Nulo é "não se sabe o que a receita pedia"; zero é "não pedia nada". */
  plannedQuantity: number | null;
  /** Nulo enquanto o consumo não foi confirmado — o lote pode estar na panela. */
  actualQuantity: number | null;
  quantityVariance: number | null;
  /** Preço médio dos lotes que a ordem separou; nulo quando ela não separou nada. */
  plannedUnitCost: number | null;
  actualUnitCost: number | null;
  plannedCost: number | null;
  actualCost: number | null;
  priceVariance: number | null;
  consumptionVariance: number | null;
  totalVariance: number | null;
  /** Falso quando falta algum dos quatro números; os campos nulos dizem qual. */
  comparable: boolean;
}

export interface VolumeVariance {
  kind: VolumeKind;
  what: string;
  /** Nulo quando ninguém definiu o esperado — perda é assim hoje (CST-002-A). */
  planned: number | null;
  actual: number;
  variance: number | null;
  variancePercent: number | null;
  comparable: boolean;
  /** Render menos ou perder mais. O sinal sozinho não diz isso. */
  unfavorable: boolean;
}

export interface VarianceGap {
  what: string;
  reason: string;
}

export interface BatchVariance {
  batchId: string;
  batchCode: string;
  plannedCost: number;
  actualCost: number;
  priceVariance: number;
  consumptionVariance: number;
  totalVariance: number;
  /** Preço + consumo explicam a diferença inteira. Vem do servidor para poder ser conferido. */
  reconciles: boolean;
  incomplete: boolean;
  materials: MaterialVariance[];
  volumes: VolumeVariance[];
  gaps: VarianceGap[];
}
