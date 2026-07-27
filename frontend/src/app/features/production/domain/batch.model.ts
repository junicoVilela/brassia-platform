export type BatchStepStatus = 'PENDING' | 'ACTIVE' | 'DONE';

export interface BatchStep {
  id: string;
  sequence: number;
  type: string;
  label: string;
  status: BatchStepStatus;
  startedAt: string | null;
  completedAt: string | null;
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
