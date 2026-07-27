export interface Transfer {
  id: string;
  destinationEquipmentId: string;
  volumeLiters: number;
  ogSg: number;
  lossesLiters: number;
  transferredAt: string;
  transferredBy: string;
}

export interface TransferRequest {
  destinationEquipmentId: string;
  volumeLiters: number;
  ogSg: number;
  lossesLiters?: number | null;
}
