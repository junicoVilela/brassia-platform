/** Entrada da trilha de auditoria (espelha AuditEntry). O changeSummary já vem mascarado. */
export interface AuditEvent {
  occurredAt: string;
  action: string;
  outcome: string;
  targetType: string;
  targetId: string;
  actorId: string;
  changeSummary: string;
}

/** Página de eventos (filtros/paginação server-side, SEC-B03). */
export interface AuditPage {
  content: AuditEvent[];
  page: number;
  size: number;
  totalElements: number;
  totalPages: number;
}

/** Filtros aplicados no servidor. Vazios são omitidos. */
export interface AuditFilter {
  action: string;
  targetType: string;
  outcome: string;
  actorId: string;
  from: string;
  to: string;
}
