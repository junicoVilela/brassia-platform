/** Custo realizado do lote (CST-001): derivado enquanto aberto, congelado quando fechado. */

export type CostCategory = 'INGREDIENT' | 'PACKAGING' | 'UTILITY' | 'LABOR';

export const CATEGORY_LABELS: Record<CostCategory, string> = {
  INGREDIENT: 'Insumo',
  PACKAGING: 'Embalagem',
  UTILITY: 'Utilidade',
  LABOR: 'Mão de obra',
};

export interface CostLine {
  category: CostCategory;
  description: string;
  /** De onde o número veio, em texto legível — a origem é rastreável por parcela. */
  source: string;
  quantity: number;
  unit: string;
  unitCost: number;
  total: number;
}

/** Parcela que ficou de fora, com o motivo. Somá-la como zero mentiria por omissão. */
export interface CostGap {
  category: CostCategory;
  reason: string;
}

export interface BatchCost {
  batchId: string;
  batchCode: string;
  /** Falso enquanto o custo é derivado: ele ainda muda se a produção mudar. */
  closed: boolean;
  incomplete: boolean;
  /** Volume transferido ao fermentador — o divisor do custo por litro. */
  volumeLiters: number;
  total: number;
  costPerLiter: number;
  totalByCategory: Partial<Record<CostCategory, number>>;
  lines: CostLine[];
  gaps: CostGap[];
  closedAt: string | null;
  note: string | null;
}
