/** Produtos, canais e preços (SAL-001). */

export interface Product {
  id: string;
  /** Sempre em maiúsculas — o backend normaliza. */
  sku: string;
  name: string;
  recipeId: string;
  containerId: string;
  active: boolean;
}

export interface SalesChannel {
  id: string;
  code: string;
  name: string;
  active: boolean;
}

export interface PriceEntry {
  amount: number;
  currency: string;
  /** A plataforma não calcula imposto; só registra se o número já o contém. */
  taxIncluded: boolean;
  validFrom: string;
  /** Nulo é "até segunda ordem". As duas pontas são inclusivas. */
  validTo: string | null;
}

export interface PriceSchedule {
  productId: string;
  channelId: string;
  entries: PriceEntry[];
}

/**
 * Um lote que dá para prometer (SAL-001-B).
 *
 * Vendável é liberado pela qualidade, dentro da validade e sem quarentena — o backend compõe as três
 * condições e só devolve o que passou nas três.
 */
export interface SellableLot {
  finishedLotId: string;
  code: string;
  batchCode: string;
  units: number;
  containerVolumeMl: number;
  packagedOn: string;
  bestBefore: string;
}
