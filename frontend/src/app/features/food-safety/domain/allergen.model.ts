/** Matriz de alergênicos (FDS-001) — o que a API devolve e o que a tela declara. */

export interface Allergen {
  id: string;
  code: string;
  name: string;
}

/**
 * Linha do ingrediente na matriz.
 *
 * <p>`declared` é o campo que a tela não pode achatar: falso é **lacuna** (ninguém respondeu), e
 * não isenção. Mostrar os dois como "sem alergênicos" é o erro que imprime "não contém glúten"
 * numa cerveja de cevada.
 */
export interface IngredientAllergenRow {
  ingredientId: string;
  code: string;
  name: string;
  declared: boolean;
  allergens: string[];
}

/** Só os equipamentos dedicados aparecem; os ausentes são compartilhados. */
export interface EquipmentDedicationRow {
  equipmentId: string;
  allergens: string[];
}

export interface ProcedureEffectivenessRow {
  procedureCode: string;
  allergens: string[];
}

export interface AllergenMatrix {
  allergens: Allergen[];
  ingredients: IngredientAllergenRow[];
  dedications: EquipmentDedicationRow[];
  procedures: ProcedureEffectivenessRow[];
}

/** Declarar é responder por inteiro: a lista enviada passa a ser a vigente. */
export interface DeclarationRequest {
  allergens: string[];
}
