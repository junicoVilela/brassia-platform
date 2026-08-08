/** Estado do gateway de IA (AIA-001). Espelha o contrato do backend; a UI não recalcula nada. */
export interface GatewayStatus {
  provider: string;
  enabled: boolean;
  models: string[];
  timeoutSeconds: number;
  budget: Budget;
  recent: Invocation[];
}

/**
 * O teto do mês e o consumo.
 *
 * `version` vem do servidor e volta para ele ao alterar o teto — é assim que a alteração de uma
 * pessoa não sobrescreve, sem aviso, a de outra.
 */
export interface Budget {
  monthlyLimit: number;
  spentThisMonth: number;
  remaining: number;
  exhausted: boolean;
  currency: string;
  version: number;
}

/** Uma chamada registrada. Sem prompt e sem resposta: o conteúdo nunca sai do backend. */
export interface Invocation {
  purpose: string;
  model: string;
  status: InvocationStatus;
  inputTokens: number;
  outputTokens: number;
  cost: number;
  currency: string;
  latencyMillis: number;
  failureReason: string | null;
  occurredAt: string;
}

export type InvocationStatus =
  | 'SUCCEEDED'
  | 'PROVIDER_DISABLED'
  | 'PROVIDER_FAILED'
  | 'REJECTED_CONTRACT'
  | 'BUDGET_EXCEEDED';

export interface ProbeResult {
  ready: boolean;
  note: string;
}

/**
 * Como cada desfecho se lê.
 *
 * O rótulo diz de quem é a providência, não só que deu errado — é a mesma distinção que o backend
 * faz nos status HTTP. "Recusada" e "Fora do ar" mandam a pessoa a lugares diferentes.
 */
export const STATUS_LABELS: Record<InvocationStatus, string> = {
  SUCCEEDED: 'Respondeu',
  PROVIDER_DISABLED: 'Sem provedor',
  PROVIDER_FAILED: 'Provedor fora do ar',
  REJECTED_CONTRACT: 'Resposta recusada',
  BUDGET_EXCEEDED: 'Orçamento esgotado',
};

/**
 * Classes do badge por desfecho.
 *
 * `PROVIDER_DISABLED` é cinza, não vermelho: uma instalação sem IA não está com defeito. Pintar de
 * vermelho o estado padrão do produto ensinaria a ignorar o vermelho.
 *
 * O amarelo vem com `text-dark` porque texto branco sobre amarelo não passa em contraste — e um
 * rótulo de desfecho que não se lê não serve para nada.
 */
export const STATUS_BADGES: Record<InvocationStatus, string> = {
  SUCCEEDED: 'bg-success',
  PROVIDER_DISABLED: 'bg-secondary',
  PROVIDER_FAILED: 'bg-danger',
  REJECTED_CONTRACT: 'bg-warning text-dark',
  BUDGET_EXCEEDED: 'bg-warning text-dark',
};
