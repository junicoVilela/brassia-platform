/** O que se quer melhorar (OPT-001). Um por vez. */
export type Objective = 'COST' | 'AVAILABILITY' | 'TECHNICAL_TARGET';

export type ConstraintKind =
  | 'MAX_COST_PER_LITER'
  | 'IBU_RANGE'
  | 'COLOR_RANGE'
  | 'KEEP_INGREDIENT'
  | 'EXCLUDE_INGREDIENT'
  | 'STOCK_ONLY';

export interface OptimizationConstraint {
  kind: ConstraintKind;
  minValue?: number | null;
  maxValue?: number | null;
  ingredientId?: string | null;
}

export interface Substitution {
  fromIngredientId: string;
  fromLabel: string;
  toIngredientId: string;
  toLabel: string;
  quantity: number;
  unit: string;
}

/** O que piorou. Nunca omitido — o ganho sem o custo faz escolher sem saber o que se troca. */
export interface TradeOff {
  dimension: string;
  description: string;
  originalValue: number;
  candidateValue: number;
}

export interface Candidate {
  label: string;
  substitutions: Substitution[];
  costPerLiter: number;
  estimatedIbu: number | null;
  estimatedColorEbc: number | null;
  score: number;
  tradeOffs: TradeOff[];
}

export interface Infeasible {
  conflictingConstraints: string[];
  explanation: string;
}

export interface OptimizationRun {
  id: string;
  recipeId: string;
  recipeVersion: number;
  objective: Objective;
  constraints: OptimizationConstraint[];
  /** Método, versões e semente: sem eles o número não se reproduz, e o que não se reproduz não se audita. */
  method: string;
  catalogVersion: string;
  seed: number | null;
  usesSeed: boolean;
  feasible: boolean;
  candidates: Candidate[];
  infeasible: Infeasible | null;
  /** Texto da IA. Fica separado das candidatas para não parecer parte do cálculo. */
  explanation: string | null;
  appliedRecipeVersionId: string | null;
  requestedBy: string;
  requestedAt: string;
}

export interface OptimizeRequest {
  recipeId: string;
  objective: Objective;
  constraints: OptimizationConstraint[];
}

export const OBJECTIVE_LABELS: Record<Objective, string> = {
  COST: 'Menor custo por litro',
  AVAILABILITY: 'Usar o que está em estoque',
  TECHNICAL_TARGET: 'Manter o alvo técnico',
};

/** O texto diz o que o objetivo sacrifica — escolher um é abrir mão dos outros. */
export const OBJECTIVE_HINTS: Record<Objective, string> = {
  COST: 'Busca a troca mais barata. Pode mudar cor e amargor — os trade-offs vêm listados.',
  AVAILABILITY: 'Prefere o que já está no estoque. Evita comprar e evita perder insumo parado.',
  TECHNICAL_TARGET: 'Prefere a troca que menos altera IBU e cor, mesmo custando mais.',
};

export const CONSTRAINT_LABELS: Record<ConstraintKind, string> = {
  MAX_COST_PER_LITER: 'Custo máximo por litro',
  IBU_RANGE: 'Faixa de IBU',
  COLOR_RANGE: 'Faixa de cor (EBC)',
  KEEP_INGREDIENT: 'Manter ingrediente',
  EXCLUDE_INGREDIENT: 'Excluir ingrediente',
  STOCK_ONLY: 'Só o que está em estoque',
};
