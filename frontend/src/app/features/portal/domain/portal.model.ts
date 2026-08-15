/** O portal do cliente (SAL-003). */

export interface CatalogItem {
  productId: string;
  sku: string;
  name: string;
  unitAmount: number;
  currency: string;
  taxIncluded: boolean;
  /** Soma do que está livre nos lotes vendáveis. Item com zero nem aparece. */
  availableUnits: number;
}

export interface PortalOrderLine {
  sku: string;
  quantity: number;
  unitAmount: number;
  currency: string;
}

/** Sem os lotes reservados: eles são rastro interno da cervejaria. */
export interface PortalOrder {
  id: string;
  code: string;
  status: 'PLACED' | 'CANCELLED' | 'FULFILLED';
  placedOn: string;
  promisedFor: string | null;
  total: number;
  currency: string;
  lines: PortalOrderLine[];
}

/**
 * O teto e o que já está comprometido.
 *
 * Mede compromisso, e não recebível: sem baixa de pagamento, um pedido entregue e não pago sai da
 * conta. `ceiling` nulo é "sem teto", e sem teto tudo cabe.
 */
export interface CreditSituation {
  ceiling: number | null;
  currency: string | null;
  committed: number;
}
