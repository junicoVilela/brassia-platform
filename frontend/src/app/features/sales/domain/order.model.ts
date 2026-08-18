/** Pedidos, reservas e promessa de entrega (SAL-002). */

export type OrderStatus = 'PLACED' | 'CANCELLED' | 'FULFILLED';

/**
 * O lote que o pedido segura.
 *
 * É o que um recall percorre: quando um lote é recolhido, "quem comprou disto?" precisa ter resposta.
 */
export interface OrderReservation {
  finishedLotId: string;
  lotCode: string;
  units: number;
  /** Congelada na reserva — é ela que sustentou a promessa de entrega. */
  bestBefore: string;
}

export interface OrderLine {
  productId: string;
  sku: string;
  quantity: number;
  /** Preço congelado: a lista muda, o pedido não. */
  unitAmount: number;
  currency: string;
  taxIncluded: boolean;
  reservations: OrderReservation[];
}

export interface SalesOrder {
  id: string;
  code: string;
  customerId: string;
  channelId: string;
  status: OrderStatus;
  placedOn: string;
  /** Nulo é "a combinar", e é estado legítimo. */
  promisedFor: string | null;
  /** Arredondado a duas casas no total, e não por linha. */
  total: number;
  currency: string;
  /**
   * Nulo é o normal: o pedido coube no teto, ou não havia teto (SAL-004).
   *
   * Preenchido, é o motivo pelo qual alguém autorizou passar dele — e um pedido acima do limite precisa
   * dizer isso na tela onde alguém o lê, e não só na trilha de auditoria.
   */
  creditOverrideReason: string | null;
  creditOverrideBy: string | null;
  lines: OrderLine[];
}

export const ORDER_STATUS_LABELS: Record<OrderStatus, string> = {
  PLACED: 'Confirmado',
  CANCELLED: 'Cancelado',
  FULFILLED: 'Atendido',
};
