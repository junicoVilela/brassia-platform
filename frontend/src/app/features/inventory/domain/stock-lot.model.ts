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
  allowNegative?: boolean;
}

export interface ReserveStockRequest {
  ingredientId: string;
  quantity: number;
  unit: StockUnit;
  orderId?: string | null;
}

export interface StockAllocation {
  lotId: string;
  quantity: number;
  unit: string;
}

export interface ReserveStockResult {
  ingredientId: string;
  reservedQuantity: number;
  unit: string;
  allocations: StockAllocation[];
}

export type LotPropertySource = 'MANUAL' | 'IMPORTED' | 'SUGGESTED';
export const LOT_PROPERTY_SOURCES: { value: LotPropertySource; label: string }[] = [
  { value: 'MANUAL', label: 'Manual' },
  { value: 'IMPORTED', label: 'Importado' },
  { value: 'SUGGESTED', label: 'Sugerido' },
];

export type LotPropertyConfidence = 'HIGH' | 'MEDIUM' | 'LOW';
export const LOT_PROPERTY_CONFIDENCES: { value: LotPropertyConfidence; label: string }[] = [
  { value: 'HIGH', label: 'Alta' },
  { value: 'MEDIUM', label: 'Média' },
  { value: 'LOW', label: 'Baixa' },
];

export interface LotProperty {
  id: string;
  property: string;
  value: number;
  unit: string | null;
  source: LotPropertySource;
  confidence: LotPropertyConfidence;
  recordedAt: string;
}

export interface RecordLotPropertyRequest {
  property: string;
  value: number;
  unit?: string | null;
  source: LotPropertySource;
  confidence: LotPropertyConfidence;
}
