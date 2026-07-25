/** Uma sessão ativa do próprio usuário. */
export interface UserSession {
  ref: string;
  createdAt: string;
  lastAccessedAt: string;
  /** true para a sessão que está fazendo a requisição (não pode se auto-revogar). */
  current: boolean;
}

/** Um evento do histórico de login do próprio usuário. */
export interface LoginEvent {
  occurredAt: string;
  outcome: string;
  reasonCode: string | null;
  /** Origem mascarada só para exibição (SEC-B02). */
  ipMasked: string | null;
  userAgentLabel: string | null;
}
