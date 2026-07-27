export interface PurchaseNeed {
  ingredientId: string;
  demand: number;
  onHand: number;
  reserved: number;
  reorderPoint: number;
  suggested: number;
  unit: string;
}
