/** Relatórios salvos e entrega programada (RPT-003). */

export type ReportKind = 'DASHBOARD' | 'BATCH_REPORT';
export type ReportSchedule = 'MANUAL' | 'DAILY' | 'WEEKLY' | 'MONTHLY';
export type RunStatus = 'SUCCEEDED' | 'REFUSED' | 'FAILED';
export type DeliveryStatus = 'PENDING' | 'DELIVERED' | 'REFUSED';

export const KIND_LABELS: Record<ReportKind, string> = {
  DASHBOARD: 'Painel operacional',
  BATCH_REPORT: 'Relatório do lote',
};

export const SCHEDULE_LABELS: Record<ReportSchedule, string> = {
  MANUAL: 'Sob demanda',
  DAILY: 'Diário',
  WEEKLY: 'Semanal',
  MONTHLY: 'Mensal',
};

export const DELIVERY_LABELS: Record<DeliveryStatus, string> = {
  PENDING: 'Pendente',
  DELIVERED: 'Entregue',
  REFUSED: 'Recusada',
};

export interface SavedReport {
  id: string;
  name: string;
  kind: ReportKind;
  /** Sobe a cada redefinição; a execução guarda contra qual versão rodou. */
  definitionVersion: number;
  filters: Record<string, string>;
  timezone: string;
  format: 'JSON';
  schedule: ReportSchedule;
  retentionDays: number;
  /** A execução roda com a alçada dele, resolvida na hora — não com a de quem pede. */
  ownerUserId: string;
  /** Usuários da plataforma. Não há e-mail livre: só de usuário se sabe a alçada. */
  recipients: string[];
  active: boolean;
  createdAt: string;
}

export interface ReportDelivery {
  userId: string;
  status: DeliveryStatus;
  detail: string | null;
  /** Sobe a cada tentativa; distingue "ainda não foi" de "não vai ser". */
  attempts: number;
  lastAttemptAt: string | null;
}

export interface ReportRun {
  id: string;
  reportId: string;
  definitionVersion: number;
  status: RunStatus;
  /** Por que não rodou. O caso que importa: o dono perdeu a alçada. */
  refusalReason: string | null;
  periodFrom: string | null;
  periodTo: string | null;
  expiresAt: string | null;
  executedAt: string;
  expired: boolean;
  deliveries: ReportDelivery[];
  /** Só vem para destinatário ou dono — o link é pessoal. */
  downloadToken: string | null;
}

export interface SavedReportRequest {
  name: string;
  kind: ReportKind;
  filters: Record<string, string>;
  timezone: string;
  format: 'JSON';
  schedule: ReportSchedule;
  retentionDays: number;
  ownerUserId: string;
  recipients: string[];
}
