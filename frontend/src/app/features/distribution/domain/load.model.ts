/** Carga, roteiro e conferência (LOG-001). */

export type LoadStatus = 'PLANNED' | 'RELEASED' | 'IN_ROUTE' | 'CLOSED' | 'CANCELLED';

export interface LoadItem {
  containerId: string;
  volumeLiters: number;
}

export interface LoadStop {
  id: string;
  sequence: number;
  customerId: string;
  /** Congelado: renomear o cliente não reescreve o romaneio que já saiu impresso. */
  customerName: string;
  windowFrom: string | null;
  windowTo: string | null;
  items: LoadItem[];
}

export interface Load {
  id: string;
  code: string;
  scheduledFor: string;
  capacityLiters: number;
  loadedLiters: number;
  remainingLiters: number;
  status: LoadStatus;
  plannedBy: string;
  /** Nunca igual a `plannedBy`. */
  releasedBy: string | null;
  releasedAt: string | null;
  driverId: string | null;
  vehicle: string | null;
  /** Depois de liberada a carga não muda — a tela esconde os botões em vez de esperar o 409. */
  frozen: boolean;
  customerCount: number;
  /** Na ordem da sequência, e não na de digitação. */
  route: LoadStop[];
}

export const LOAD_STATUS_LABELS: Record<LoadStatus, string> = {
  PLANNED: 'Em montagem',
  RELEASED: 'Conferida',
  IN_ROUTE: 'Na rua',
  CLOSED: 'Encerrada',
  CANCELLED: 'Cancelada',
};

export const LOAD_STATUS_HELP: Record<LoadStatus, string> = {
  PLANNED: 'Ainda muda. Falta a conferência de outra pessoa.',
  RELEASED: 'Conferida e liberada. Não muda mais — se mudar, alguém confere de novo.',
  IN_ROUTE: 'Saiu com o responsável.',
  CLOSED: 'Voltou e foi encerrada.',
  CANCELLED: 'Não saiu.',
};

/** Por que o vasilhame não pode sair. Cada motivo leva a uma ação diferente. */
export const NOT_SHIPPABLE_REASONS: Record<string, string> = {
  container_empty: 'O vasilhame está vazio. Carga é o que sai cheio.',
  wrong_state: 'O vasilhame não está no depósito — já saiu, está no cliente ou na oficina.',
  not_released: 'A qualidade ainda não liberou o lote. Cobre a liberação antes de despachar.',
  expired: 'O lote está vencido.',
  quarantined: 'O lote está em quarentena.',
  quarantine_suspected: 'O lote está sob suspeita de quarentena.',
  shelf_life_unknown: 'A validade do lote ainda não foi apurada.',
  already_loaded: 'O vasilhame já está em outra carga aberta.',
  container_not_found: 'Vasilhame não encontrado.',
};
