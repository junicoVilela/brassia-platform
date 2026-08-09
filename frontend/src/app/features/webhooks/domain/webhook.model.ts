/** Estado de uma assinatura de webhook (INT-002). */
export type SubscriptionStatus = 'ACTIVE' | 'PAUSED' | 'REVOKED';

/**
 * Estado de uma entrega no outbox.
 *
 * `EXHAUSTED` não some da lista, e é de propósito: uma entrega que desiste em silêncio é a pior falha de
 * integração — o outro lado nunca soube do evento, e nós também não sabemos que ele não soube.
 */
export type DeliveryStatus = 'PENDING' | 'DELIVERED' | 'EXHAUSTED';

export interface WebhookSubscription {
  id: string;
  name: string;
  endpoint: string;
  events: string[];
  status: SubscriptionStatus;
  /** Os primeiros caracteres do segredo. Serve para conferir, não para usar. */
  secretHint: string;
  createdAt: string;
  version: number;
}

export interface WebhookDelivery {
  id: string;
  eventType: string;
  eventId: string;
  status: DeliveryStatus;
  attempts: number;
  nextAttemptAt: string | null;
  deliveredAt: string | null;
  lastResponseStatus: number | null;
  lastError: string | null;
  createdAt: string;
}

/** A resposta da criação — a única vez em que o segredo existe do lado do cliente. */
export interface CreatedSubscription {
  subscription: WebhookSubscription;
  secret: string;
  warning: string;
}

export interface CreateSubscriptionRequest {
  name: string;
  endpoint: string;
  events: string[];
}

export const SUBSCRIPTION_STATUS_LABELS: Record<SubscriptionStatus, string> = {
  ACTIVE: 'Ativa',
  PAUSED: 'Pausada',
  REVOKED: 'Revogada',
};

export const DELIVERY_STATUS_LABELS: Record<DeliveryStatus, string> = {
  PENDING: 'Na fila',
  DELIVERED: 'Entregue',
  EXHAUSTED: 'Desistiu',
};

/** Como cada evento se lê. Os nomes externos são estáveis: são contrato com quem integra. */
export const EVENT_LABELS: Record<string, string> = {
  'brew_order.released': 'Ordem de produção liberada',
  'brew_order.started': 'Produção iniciada',
  'brew_order.cancelled': 'Ordem cancelada',
  'recipe.published': 'Receita publicada',
  'cleaning_cycle.released': 'Ciclo de limpeza liberado',
  'sensor_reading.flagged': 'Leitura de sensor sinalizada',
};
