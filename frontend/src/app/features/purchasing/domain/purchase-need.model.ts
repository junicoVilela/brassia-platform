export interface PurchaseNeed {
  ingredientId: string;
  demand: number;
  onHand: number;
  reserved: number;
  suggested: number;
  unit: string;
}
