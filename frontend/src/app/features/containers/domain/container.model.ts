/** Contêineres retornáveis (CON-001). */

export type ContainerKind = 'KEG' | 'CASK' | 'GROWLER';

/** De quem é o vasilhame — o de terceiro **não** é ativo da cervejaria. */
export type Ownership = 'OWN' | 'CUSTOMER' | 'POOL';

export type ContainerCondition = 'GOOD' | 'DAMAGED' | 'CONDEMNED';

/**
 * Onde o contêiner está no ciclo.
 *
 * `RETURNED` não é `EMPTY`: o que voltou do cliente está sujo até que alguém diga o contrário.
 */
export type ContainerState =
  | 'EMPTY'
  | 'FILLED'
  | 'IN_TRANSIT'
  | 'AT_CUSTOMER'
  | 'RETURNED'
  | 'IN_MAINTENANCE'
  | 'RETIRED';

export type IdentifierTechnology = 'QR' | 'BARCODE' | 'RFID';

export interface Container {
  id: string;
  code: string;
  kind: ContainerKind;
  nominalCapacityLiters: number;
  ownership: Ownership;
  condition: ContainerCondition;
  state: ContainerState;
  /** Nulo quando nunca foi inspecionado — e nesse caso ele não pode ser enchido. */
  inspectionValidUntil: string | null;
  /** Já composto pelo servidor: avaria, estado e inspeção juntos. A tela não recalcula a regra. */
  fillable: boolean;
  retiredAt: string | null;
  retirementReason: string | null;
}

/** Uma etiqueta. Não carrega alçada: ela responde "qual keg é esta", e nada mais. */
export interface ContainerIdentifier {
  id: string;
  value: string;
  technology: IdentifierTechnology;
  assignedAt: string;
  /** Depois disso ela não resolve mais — mas continua explicando as leituras antigas. */
  retiredAt: string | null;
}

export const STATE_LABELS: Record<ContainerState, string> = {
  EMPTY: 'Vazio e liberado',
  FILLED: 'Cheio',
  IN_TRANSIT: 'Em rota',
  AT_CUSTOMER: 'No cliente',
  RETURNED: 'Voltou — a lavar',
  IN_MAINTENANCE: 'Em manutenção',
  RETIRED: 'Baixado',
};

/**
 * O que cada estado significa na prática.
 *
 * A dupla `RETURNED`/`EMPTY` é a que o operador confunde, e a confusão enche um keg que ninguém lavou.
 */
export const STATE_HELP: Record<ContainerState, string> = {
  EMPTY: 'Limpo e pronto para encher.',
  FILLED: 'Com cerveja, ainda na casa.',
  IN_TRANSIT: 'Saiu para entrega.',
  AT_CUSTOMER: 'Na mão do cliente.',
  RETURNED: 'De volta na casa, ainda sujo. Alguém precisa liberar antes de encher de novo.',
  IN_MAINTENANCE: 'Na oficina. Volta vazio e em boa condição.',
  RETIRED: 'Fora do inventário, com motivo registrado. Nada mais acontece com ele.',
};

export const CONDITION_LABELS: Record<ContainerCondition, string> = {
  GOOD: 'Boa',
  DAMAGED: 'Avariado',
  CONDEMNED: 'Condenado',
};

export const OWNERSHIP_LABELS: Record<Ownership, string> = {
  OWN: 'Da casa',
  CUSTOMER: 'Do cliente',
  POOL: 'De pool',
};

/** O motivo pelo qual não dá para encher, na língua de quem opera. */
export const NOT_FILLABLE_REASONS: Record<string, string> = {
  damaged: 'Está avariado — encher um vasilhame com vazamento perde a cerveja e o tempo.',
  condemned: 'Foi condenado e só espera baixa.',
  inspection_expired: 'A inspeção está vencida. Vaso de pressão sem inspeção em dia é risco físico.',
  not_ready: 'Não está vazio e liberado.',
};

/** Onde o vasilhame está. Grosso de propósito: a rota fina é da LOG-001. */
export type LocationKind = 'WAREHOUSE' | 'IN_TRANSIT' | 'CUSTOMER' | 'THIRD_PARTY';

export const LOCATION_LABELS: Record<LocationKind, string> = {
  WAREHOUSE: 'Depósito',
  IN_TRANSIT: 'Na rua',
  CUSTOMER: 'No cliente',
  THIRD_PARTY: 'Em terceiro',
};

/**
 * Um período em que um lote esteve dentro do vasilhame (CON-002).
 *
 * Evento, e não campo: é o que permite a um keg que vive anos dizer o que carregou em cada época.
 */
export interface ContainerFill {
  id: string;
  finishedLotId: string;
  /** Congelado no enchimento — é o que aparece no aviso de recall. */
  lotCode: string;
  volumeLiters: number;
  filledAt: string;
  /** Fecha o período. Nulo enquanto o conteúdo está dentro. */
  emptiedAt: string | null;
  current: boolean;
}

export interface ContainerLocation {
  id: string;
  kind: LocationKind;
  place: string | null;
  recordedAt: string;
}

/** Por que o conteúdo não pôde entrar — outra coisa que o motivo do vasilhame. */
export const FILL_REFUSAL_REASONS: Record<string, string> = {
  already_full: 'Já há um lote dentro. Dois lotes no mesmo vasilhame seria mistura sem registro.',
  over_capacity: 'O volume informado não cabe no vasilhame.',
  content_required: 'Encher exige dizer qual lote entrou.',
  expired: 'O lote está vencido.',
  quarantined: 'O lote está em quarentena.',
  quarantine_suspected: 'O lote está sob suspeita de quarentena.',
};
