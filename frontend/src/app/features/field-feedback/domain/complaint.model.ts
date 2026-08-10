/** Quão grave é a reclamação (FLD-001). */
export type Severity = 'PREFERENCE' | 'QUALITY' | 'SYSTEMIC' | 'SAFETY';

export type ComplaintCategory =
  | 'OFF_FLAVOR'
  | 'APPEARANCE'
  | 'CARBONATION'
  | 'PACKAGING'
  | 'FOREIGN_BODY'
  | 'ILLNESS'
  | 'OTHER';

export type ComplaintStatus = 'OPEN' | 'UNDER_ANALYSIS' | 'CLOSED';

export type SampleStatus = 'RETAINED' | 'WITH_CONSUMER' | 'UNAVAILABLE' | 'UNKNOWN';

export interface StorageInfo {
  temperatureCelsius: number | null;
  daysSincePurchase: number | null;
  exposedToLight: boolean | null;
  notes: string | null;
  /** Se alguém chegou a perguntar. Falso não é "estava tudo bem". */
  conditionsKnown: boolean;
}

export interface SampleInfo {
  status: SampleStatus;
  location: string | null;
  analyzable: boolean;
}

export interface RequiredActionInfo {
  code: string;
  description: string;
}

export interface ActionOutcome {
  action: string;
  fulfilled: boolean;
  referenceId: string | null;
  justification: string | null;
  decidedBy: string;
  decidedAt: string;
}

/**
 * A reclamação.
 *
 * **Não há campo de dado pessoal aqui, e é intencional.** Nome, telefone e endereço vêm de uma chamada
 * separada, com permissão própria e leitura auditada. Um modelo sem o campo não vaza o dado por
 * esquecimento quando alguém montar uma tela nova.
 */
export interface Complaint {
  id: string;
  batchId: string;
  reference: string | null;
  category: ComplaintCategory;
  severity: Severity;
  description: string;
  storage: StorageInfo;
  sample: SampleInfo;
  requiredActions: RequiredActionInfo[];
  /** O que ainda falta. A diferença para requiredActions é o que impede o encerramento. */
  pendingActions: string[];
  outcomes: ActionOutcome[];
  status: ComplaintStatus;
  closingNote: string | null;
  closedBy: string | null;
  closedAt: string | null;
  registeredBy: string;
  registeredAt: string;
}

/** O contato, sempre numa resposta à parte. Apagado vem vazio com `erased`. */
export interface ComplainantContact {
  name: string | null;
  email: string | null;
  phone: string | null;
  address: string | null;
  erased: boolean;
  erasedAt: string | null;
  recordedAt: string;
}

export interface RegisterComplaintRequest {
  batchId: string;
  reference?: string;
  category: ComplaintCategory;
  severity: Severity;
  description: string;
  storage?: {
    temperatureCelsius?: number | null;
    daysSincePurchase?: number | null;
    exposedToLight?: boolean | null;
    notes?: string | null;
  };
  sample?: { status: SampleStatus; location?: string | null };
  contact?: { name?: string; email?: string; phone?: string; address?: string };
}

export const SEVERITY_LABELS: Record<Severity, string> = {
  PREFERENCE: 'Preferência',
  QUALITY: 'Desvio de qualidade',
  SYSTEMIC: 'Suspeita de falha de processo',
  SAFETY: 'Risco à saúde',
};

/** O texto diz a consequência, não só o nome — é o que faz alguém classificar com cuidado. */
export const SEVERITY_HINTS: Record<Severity, string> = {
  PREFERENCE: 'A cerveja está conforme; o consumidor esperava outra coisa.',
  QUALITY: 'Desvio perceptível, sem risco à saúde.',
  SYSTEMIC: 'Sugere falha que alcança além deste exemplar — exigirá investigação de causa.',
  SAFETY: 'Corpo estranho, contaminação ou embalagem violada — exigirá quarentena e investigação.',
};

export const SEVERITY_CLASSES: Record<Severity, string> = {
  PREFERENCE: 'bg-secondary-subtle text-secondary-emphasis',
  QUALITY: 'bg-info-subtle text-info-emphasis',
  SYSTEMIC: 'bg-warning-subtle text-warning-emphasis',
  SAFETY: 'bg-danger-subtle text-danger-emphasis',
};

export const CATEGORY_LABELS: Record<ComplaintCategory, string> = {
  OFF_FLAVOR: 'Sabor ou aroma estranho',
  APPEARANCE: 'Aparência',
  CARBONATION: 'Carbonatação',
  PACKAGING: 'Embalagem',
  FOREIGN_BODY: 'Corpo estranho',
  ILLNESS: 'Alegação de mal-estar',
  OTHER: 'Outro',
};

export const SAMPLE_LABELS: Record<SampleStatus, string> = {
  RETAINED: 'Retida pela cervejaria',
  WITH_CONSUMER: 'Com o consumidor',
  UNAVAILABLE: 'Descartada ou consumida',
  UNKNOWN: 'Não perguntado',
};

export const STATUS_LABELS: Record<ComplaintStatus, string> = {
  OPEN: 'Aberta',
  UNDER_ANALYSIS: 'Em análise',
  CLOSED: 'Encerrada',
};

/** Categorias que exigem ação por si só, independentemente da severidade escolhida. */
export const RISK_CATEGORIES: ComplaintCategory[] = ['FOREIGN_BODY', 'ILLNESS'];
