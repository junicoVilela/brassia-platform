export const ALERT_KINDS: { value: string; label: string }[] = [
  { value: 'ADDITION', label: 'Adição' },
  { value: 'STEP', label: 'Etapa' },
  { value: 'MEASUREMENT', label: 'Medição' },
  { value: 'DECISION', label: 'Decisão' },
];

export interface BatchAlert {
  id: string;
  kind: string;
  message: string;
  plannedAt: string | null;
  occurredAt: string | null;
  status: string;
  createdAt: string;
  confirmedAt: string | null;
}

export interface CreateAlertRequest {
  kind: string;
  message: string;
  plannedAt?: string | null;
  occurredAt?: string | null;
}
