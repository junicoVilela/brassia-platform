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
