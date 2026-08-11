/** Lote de produto acabado (TRC-001-B) e a sua saída (TRC-001-D). */

export interface FinishedLot {
  id: string;
  code: string;
  runId: string;
  planId: string;
  batchId: string;
  batchCode: string;
  containerId: string;
  containerVolumeMl: number;
  /** Só as unidades boas: rejeito consumiu embalagem e não virou produto. */
  units: number;
  volumeLiters: number;
  packagedOn: string;
}

export interface Shipment {
  id: string;
  finishedLotId: string;
  destination: string;
  /** Pode ser nulo — destino sem contato é lacuna que o recall mostra, não esconde. */
  contact: string | null;
  units: number;
  shippedOn: string;
  note: string | null;
  /** Preenchido quando a expedição foi estornada (FDS-003-A). Estornada não conta no recall. */
  reversedAt: string | null;
  reversalReason: string | null;
}

export interface RecordShipmentRequest {
  finishedLotId: string;
  destination: string;
  contact: string | null;
  units: number;
  shippedOn: string;
  note: string | null;
}
