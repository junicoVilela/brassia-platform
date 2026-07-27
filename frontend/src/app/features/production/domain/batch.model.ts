export interface BatchStep {
  sequence: number;
  type: string;
  label: string;
}

export interface Batch {
  id: string;
  orderId: string;
  code: string;
  recipeId: string;
  recipeVersion: number;
  recipeName: string;
  volumeLiters: number;
  status: string;
  startedAt: string;
  steps: BatchStep[];
}
