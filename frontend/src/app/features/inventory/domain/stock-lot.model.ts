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
