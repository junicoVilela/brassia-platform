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
  purchasePackageSize: number | null;
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
  purchasePackageSize?: number | null;
  attributes?: Record<string, string>;
}

export interface PropertyRange {
  min: number | null;
  max: number | null;
  unit: string | null;
}

export interface TechnicalProfile {
  ingredientId: string;
  manufacturer: string | null;
  origin: string | null;
  form: string | null;
  purpose: string | null;
  laboratory: string | null;
  labCode: string | null;
  ranges: Record<string, PropertyRange>;
  descriptors: string[];
  sourceId: string | null;
  sourceName: string | null;
  status: string;
}

export interface SubstitutionComparison {
  property: string;
  target: number;
  candidate: number;
  unit: string | null;
  similar: boolean;
}

export interface SubstitutionMatch {
  ingredientId: string;
  code: string;
  name: string;
  sourceName: string | null;
  score: number;
  confidence: string;
  comparisons: SubstitutionComparison[];
}

export interface Substitutions {
  ingredientId: string;
  code: string;
  name: string;
  hasProfile: boolean;
  matches: SubstitutionMatch[];
}

export interface CreateTechnicalProfileRequest {
  manufacturer: string | null;
  origin: string | null;
  form: string | null;
  purpose: string | null;
  laboratory: string | null;
  labCode: string | null;
  ranges: Record<string, PropertyRange>;
  descriptors: string[];
  sourceId: string | null;
  sourceName: string | null;
}
