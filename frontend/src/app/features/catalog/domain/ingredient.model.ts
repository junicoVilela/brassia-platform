export type IngredientType = 'MALT' | 'HOP' | 'YEAST' | 'SALT' | 'ADJUNCT' | 'PACKAGING';

export type MeasurementUnit = 'KG' | 'G' | 'MG' | 'L' | 'ML' | 'UNIT' | 'PACK';

export const INGREDIENT_TYPES: IngredientType[] = ['MALT', 'HOP', 'YEAST', 'SALT', 'ADJUNCT', 'PACKAGING'];

export const MEASUREMENT_UNITS: MeasurementUnit[] = ['KG', 'G', 'MG', 'L', 'ML', 'UNIT', 'PACK'];

export interface Ingredient {
  id: string;
  type: IngredientType;
  code: string;
  name: string;
  useUnit: MeasurementUnit;
  purchaseUnit: MeasurementUnit;
  attributes: Record<string, string>;
  active: boolean;
  version: number;
}

export interface RegisterIngredientRequest {
  type: IngredientType;
  code: string;
  name: string;
  useUnit: MeasurementUnit;
  purchaseUnit: MeasurementUnit;
  attributes?: Record<string, string>;
}
