/** Cliente, contato e consentimento (CRM-001). */

export interface Customer {
  id: string;
  legalName: string;
  tradeName: string | null;
  /** Fantasia quando existe, razão social quando não — o backend já decide qual. */
  displayName: string;
  taxId: string | null;
  active: boolean;
}

/** As três finalidades. `TRANSACTIONAL` se apoia em contrato e não aparece como algo a consentir. */
export type ContactPurpose = 'TRANSACTIONAL' | 'MARKETING' | 'SURVEY';

export type LegalBasis = 'CONTRACT' | 'CONSENT';

export interface PurposeState {
  purpose: ContactPurpose;
  basis: LegalBasis;
  allowedNow: boolean;
}

export interface ConsentEntry {
  purpose: ContactPurpose;
  decision: 'GRANTED' | 'REVOKED';
  /** O instante do mundo, e não o da digitação. */
  decidedAt: string;
  source: string;
}

export interface Contact {
  id: string;
  customerId: string;
  /** Ausente depois da anonimização — a tela mostra "contato anonimizado", não um campo vazio. */
  name: string | null;
  email: string | null;
  phone: string | null;
  role: string | null;
  anonymized: boolean;
  anonymizedAt: string | null;
  purposes: PurposeState[];
  consentHistory: ConsentEntry[];
}

/** Rótulos em português, num lugar só: a tela não deve mostrar o enum do backend. */
export const PURPOSE_LABELS: Record<ContactPurpose, string> = {
  TRANSACTIONAL: 'Avisos da venda',
  MARKETING: 'Ofertas e novidades',
  SURVEY: 'Pesquisa de satisfação',
};

export const BASIS_LABELS: Record<LegalBasis, string> = {
  CONTRACT: 'Base contratual',
  CONSENT: 'Depende de consentimento',
};
