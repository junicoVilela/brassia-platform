export type RecipeStage = 'MASH' | 'BOIL' | 'WHIRLPOOL' | 'FERMENTATION' | 'PACKAGING';

export const RECIPE_STAGES: RecipeStage[] = ['MASH', 'BOIL', 'WHIRLPOOL', 'FERMENTATION', 'PACKAGING'];

export type RecipeUnit = 'KG' | 'G' | 'MG' | 'L' | 'ML' | 'UNIT';

export const RECIPE_UNITS: RecipeUnit[] = ['KG', 'G', 'MG', 'L', 'ML', 'UNIT'];

export interface RecipeSummary {
  id: string;
  name: string;
  status: 'DRAFT' | 'PUBLISHED' | 'ARCHIVED';
  batchVolumeLiters: number;
  version: number;
}

export interface CreateRecipeItem {
  ingredientId: string;
  stage: RecipeStage;
  quantity: number;
  unit: RecipeUnit;
  timingMinutes?: number | null;
  percentage?: number | null;
}

export interface CreateRecipeRequest {
  name: string;
  equipmentId: string;
  batchVolumeLiters: number;
  targetOgPoints?: number | null;
  targetIbu?: number | null;
  targetColorEbc?: number | null;
  targetAbv?: number | null;
  boilTimeMinutes?: number | null;
  items: CreateRecipeItem[];
}

export interface CreatedRecipe {
  id: string;
  name: string;
  status: string;
}

export interface RecipeDifference {
  field: string;
  left: string | null;
  right: string | null;
}

export interface RecipeComparison {
  leftId: string;
  rightId: string;
  differences: RecipeDifference[];
}

export interface VolumeBalance {
  recipeId: string;
  grainMassKg: number;
  finalVolumeLiters: number;
  grainAbsorptionLiters: number;
  evaporationLiters: number;
  lossesLiters: number;
  preBoilVolumeLiters: number;
  totalWaterLiters: number;
  method: string;
}

export interface MetricCheck {
  value: number;
  target: number | null;
  tolerance: number;
  deviation: number | null;
  withinTolerance: boolean | null;
}

export interface CalculatedMetrics {
  recipeId: string;
  method: string;
  version: number;
  ogPoints: number;
  ogSg: number;
  fgPoints: number;
  fgSg: number;
  abv: number;
  ibu: number;
  colorEbc: number;
  attenuationPercent: number;
  ogCheck: MetricCheck;
  ibuCheck: MetricCheck;
  colorCheck: MetricCheck;
  abvCheck: MetricCheck;
}

/** Converte um valor de form (0/'' vira null nos campos opcionais) num item de request. */
export function toCreateRecipeItem(value: {
  ingredientId: string;
  stage: RecipeStage;
  quantity: number;
  unit: RecipeUnit;
  timingMinutes: number | null;
  percentage: number | null;
}): CreateRecipeItem {
  return {
    ingredientId: value.ingredientId,
    stage: value.stage,
    quantity: value.quantity,
    unit: value.unit,
    timingMinutes: value.timingMinutes || null,
    percentage: value.percentage || null,
  };
}
