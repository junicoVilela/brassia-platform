export type BrewOrderStatus =
  | 'DRAFT'
  | 'RELEASED'
  | 'IN_PRODUCTION'
  | 'FERMENTING'
  | 'CONDITIONING'
  | 'PACKAGED'
  | 'CLOSED'
  | 'CANCELLED';

export interface BrewOrderSummary {
  id: string;
  code: string;
  recipeId: string;
  recipeVersion: number;
  recipeName: string;
  volumeLiters: number;
  status: BrewOrderStatus;
}

export interface CreateBrewOrderRequest {
  recipeId: string;
  volumeLiters: number;
}

export interface CreatedBrewOrder {
  id: string;
  code: string;
  status: string;
}

export interface BrewOrderDetail {
  id: string;
  code: string;
  recipeId: string;
  recipeVersion: number;
  volumeLiters: number;
  status: BrewOrderStatus;
  recipe: {
    id: string;
    version: number;
    name: string;
    ogSg: number;
    fgSg: number;
    abv: number;
    ibu: number;
    colorEbc: number;
  };
  equipment: {
    id: string;
    capacityLiters: number;
    deadSpaceLiters: number;
    mashEfficiencyPercent: number;
    boilOffLitersPerHour: number;
  };
}
