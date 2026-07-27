export interface ShoppingListItem {
  ingredientId: string;
  ingredientCode: string | null;
  ingredientName: string | null;
  demand: number;
  onHand: number;
  reserved: number;
  suggested: number;
  unit: string;
  purchaseQuantity: number;
  purchaseUnit: string;
  packages: number | null;
  unitCost: number | null;
  estimatedCost: number | null;
}

export interface ShoppingListGroup {
  supplierId: string | null;
  supplierName: string;
  items: ShoppingListItem[];
  estimatedTotal: number | null;
}
