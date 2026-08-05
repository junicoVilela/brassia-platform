/** Recall (FDS-003): a decisão registrada, o escopo derivado e a comunicação. */

import { LineageGap, LineageNode } from './genealogy.model';
import { Affected } from './quarantine.model';

export type RecallStatus = 'OPEN' | 'CLOSED';

export interface Recall {
  id: string;
  code: string;
  origin: LineageNode;
  reason: string;
  status: RecallStatus;
  openedAt: string;
  closedAt: string | null;
  closingSummary: string | null;
}

export type NotificationStatus = 'PENDING' | 'NOTIFIED';

/**
 * Um destino do dossiê. É a parte <strong>guardada</strong> do recall: avisar um cliente é fato
 * sobre o que a cervejaria fez, e derivá-lo do grafo apagaria a prova de que ele foi avisado.
 */
export interface RecallNotification {
  id: string;
  shipmentId: string;
  finishedLotCode: string;
  destination: string;
  contact: string | null;
  units: number;
  status: NotificationStatus;
  channel: string | null;
  note: string | null;
  notifiedAt: string | null;
}

/** Expedição que entrou no escopo depois da abertura: o lote saiu depois. */
export interface NewDestination {
  shipmentId: string;
  destination: string;
  contact: string | null;
  units: number;
}

export interface RecallDossier {
  recall: Recall;
  notifications: RecallNotification[];
  pending: number;
  /** Percentual dos destinos conhecidos já comunicados — leia junto com `gaps`. */
  coverage: number;
  truncated: boolean;
  scope: Affected[];
  newDestinations: NewDestination[];
  /** Lotes do escopo sem expedição registrada: não se sabe onde estão. */
  gaps: LineageGap[];
}
