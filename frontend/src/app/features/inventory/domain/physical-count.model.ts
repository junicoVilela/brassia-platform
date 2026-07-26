export type PhysicalCountStatus = 'OPEN' | 'APPROVED';

export interface PhysicalCountLine {
  lotId: string;
  ingredientId: string;
  unit: string;
  countedQuantity: number;
  systemQuantity: number;
  difference: number;
}

export interface PhysicalCount {
  id: string;
  status: PhysicalCountStatus;
  createdAt: string;
  approvedAt: string | null;
  lines: PhysicalCountLine[];
}

export interface CreateCountRequest {
  lines: { lotId: string; countedQuantity: number }[];
}
