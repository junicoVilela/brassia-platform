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

export interface AuditFilter {
  term: string;
  outcome: string;
  from: string;
  to: string;
}
