/**
 * Uma proposta de comando do copiloto (AIA-003).
 *
 * A proposta não é um comando: ela espera a decisão de uma pessoa, e nada no sistema muda por causa dela.
 * `requiredPermission` e `canConfirm` viajam juntos porque a tela precisa dizer *qual* alçada falta — um
 * botão desabilitado sem nome de permissão deixa quem lê sem o que fazer a respeito.
 */
export interface CommandProposal {
  id: string;
  action: ProposedAction;
  label: string;
  parameters: Record<string, string>;
  rationale: string;
  /** A alçada do comando de verdade — nunca `ai.command.propose`. */
  requiredPermission: string;
  /** Onde o comando vive. O aceite entrega este destino em vez de executar. */
  executionRoute: string;
  /** Quem **pediu** a proposta. A IA não propõe sozinha. */
  proposedBy: string;
  proposedAt: string;
  expiresAt: string;
  status: ProposalStatus;
  /** Derivado do prazo na leitura. Uma proposta não muda de estado porque o tempo passou. */
  expired: boolean;
  canConfirm: boolean;
  /** Quem **confirmou**, que não é necessariamente quem pediu. */
  decidedBy: string | null;
  decidedAt: string | null;
  decisionNote: string | null;
}

/** A allowlist fechada. Nada fora daqui chega a ser proposta. */
export type ProposedAction = 'CLOSE_BATCH_COST' | 'OPEN_NON_CONFORMITY' | 'SCHEDULE_CLEANING_CYCLE';

/** Não há `EXPIRED` nem `EXECUTED`: vencimento é derivado, e execução não acontece aqui. */
export type ProposalStatus = 'PENDING' | 'ACCEPTED' | 'REJECTED';

export const STATUS_LABELS: Record<ProposalStatus, string> = {
  PENDING: 'Aguardando decisão',
  ACCEPTED: 'Confirmada',
  REJECTED: 'Descartada',
};

export const STATUS_BADGES: Record<ProposalStatus, string> = {
  PENDING: 'bg-warning text-dark',
  ACCEPTED: 'bg-success',
  REJECTED: 'bg-secondary',
};

/** Como nomear a alçada que falta, em vez de só desabilitar o botão. */
export const PERMISSION_LABELS: Record<string, string> = {
  'costing.cost.close': 'fechar o custo do lote',
  'quality.nc.manage': 'gerenciar não conformidades',
  'sanitation.cycle.execute': 'executar ciclos de limpeza',
};
