/** Alerta de segurança (espelha AlertView). */
export interface SecurityAlert {
  id: string;
  userId: string | null;
  alertType: string;
  severity: string;
  status: string;
  evidence: Record<string, unknown>;
  createdAt: string;
}

export type AlertStatusUpdate = 'ACKNOWLEDGED' | 'RESOLVED';
