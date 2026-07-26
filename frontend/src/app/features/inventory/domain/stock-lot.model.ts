export type StockUnit = 'KG' | 'G' | 'MG' | 'L' | 'ML' | 'UNIT';
export const STOCK_UNITS: StockUnit[] = ['KG', 'G', 'MG', 'L', 'ML', 'UNIT'];

export type StockInspection = 'APPROVED' | 'BLOCKED';

export interface StockLot {
  id: string;
  ingredientId: string;
  supplierId: string;
  supplierLotCode: string | null;
  receivedQuantity: number;
  unit: StockUnit;
  unitCost: number;
  expiryDate: string | null;
  inspection: StockInspection;
  available: boolean;
}

export interface ReceiveStockLotRequest {
  ingredientId: string;
  supplierId: string;
  supplierLotCode?: string | null;
  quantity: number;
  unit: StockUnit;
  unitCost: number;
  expiryDate?: string | null;
  inspection: StockInspection;
}

export type MovementType = 'CONSUMPTION' | 'RETURN' | 'LOSS' | 'ADJUSTMENT_IN' | 'ADJUSTMENT_OUT';
export const MOVEMENT_TYPES: { value: MovementType; label: string }[] = [
  { value: 'CONSUMPTION', label: 'Consumo' },
  { value: 'RETURN', label: 'Devolução' },
  { value: 'LOSS', label: 'Perda' },
  { value: 'ADJUSTMENT_IN', label: 'Ajuste (+)' },
  { value: 'ADJUSTMENT_OUT', label: 'Ajuste (−)' },
];

export interface StockBalance {
  onHand: number;
  reserved: number;
  available: number;
}

export interface StockMovement {
  id: string;
  type: string;
  quantity: number;
  onHandDelta: number;
  reservedDelta: number;
  reason: string | null;
  occurredAt: string;
}

export interface RecordMovementRequest {
  type: MovementType;
  quantity: number;
  reason?: string | null;
}
