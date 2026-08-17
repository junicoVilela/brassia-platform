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

/** O que aconteceu na parada (LOG-002). "Não entregue" não é um motivo só. */
export type DeliveryOutcome = 'DELIVERED' | 'PARTIAL' | 'REFUSED' | 'ABSENT' | 'RESCHEDULED';

export const OUTCOME_LABELS: Record<DeliveryOutcome, string> = {
  DELIVERED: 'Entregue',
  PARTIAL: 'Entrega parcial',
  REFUSED: 'Recusada',
  ABSENT: 'Ninguém no local',
  RESCHEDULED: 'Remarcada',
};

/**
 * Uma prova de entrega.
 *
 * Ela não se edita: quando algo está errado, um registro novo aponta para este, e os dois ficam.
 */
export interface ProofOfDelivery {
  id: string;
  stopId: string;
  outcome: DeliveryOutcome;
  occurredAt: string;
  recordedBy: string;
  delivered: string[];
  collected: string[];
  note: string | null;
  /** A janela era compromisso; perdê-la se explica depois, e não impede a entrega. */
  outsideWindow: boolean;
  /** Só o tipo e a finalidade — a chave do arquivo não viaja na listagem. */
  mediaKind: 'SIGNATURE' | 'PHOTO' | null;
  mediaPurpose: string | null;
  consentedByName: string | null;
  /** Três casas decimais, ~100 m: confirma o endereço sem virar rastro de pessoa. */
  latitude: number | null;
  longitude: number | null;
  /** Quando presente, este registro é a correção — e a original continua na lista. */
  correctsProofId: string | null;
}

/** Por que a prova não pôde ser registrada agora. */
export const NOT_RECORDABLE_REASONS: Record<string, string> = {
  load_not_on_the_road: 'A carga ainda não saiu. Registre a saída antes das entregas.',
  already_recorded:
    'Esta parada já tem prova de entrega. Para mudar o que ficou registrado, use a correção.',
  not_in_stop: 'Um dos vasilhames não estava nesta parada.',
  no_original: 'Não há prova de entrega para corrigir nesta parada.',
};
