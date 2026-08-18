/** A baixa de pagamento do pedido (DEB-SAL-002). */

/**
 * Um lançamento.
 *
 * O estorno é **outro lançamento**, com `reversal` verdadeiro e `reversesPaymentId` apontando para o
 * original: os dois ficam, e a soma explica a si mesma. Corrigir por cima faria a linha parecer original
 * dizendo outra coisa — e é essa linha que alguém confere com o extrato seis meses depois.
 */
export interface Payment {
  id: string;
  /** Sempre positivo, inclusive no estorno: o sinal vem da existência do estorno, e não do número. */
  amount: number;
  currency: string;
  receivedOn: string;
  method: string;
  /** No estorno é o motivo, e é obrigatório. */
  note: string | null;
  recordedBy: string;
  recordedAt: string;
  reversal: boolean;
  reversesPaymentId: string | null;
}

export interface OrderPayments {
  orderId: string;
  total: number;
  /** O que entrou, já descontados os estornos. */
  received: number;
  outstanding: number;
  currency: string;
  payments: Payment[];
}
