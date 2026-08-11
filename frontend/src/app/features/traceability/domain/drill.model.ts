/** Simulado de recall (FDS-004): treinar a localização, sem afetar estoque real. */

import { LineageGap, LineageNode } from './genealogy.model';

export type DrillStatus = 'RUNNING' | 'FINISHED';

export interface RecallDrill {
  id: string;
  code: string;
  origin: LineageNode;
  note: string | null;
  status: DrillStatus;
  startedAt: string;
  finishedAt: string | null;
  unitsInScope: number | null;
  unitsLocated: number | null;
  /** Nulo quando não havia nada no escopo: não achar o que não existe não é cobertura. */
  locatedPercent: number | null;
  destinationsReached: number | null;
  gapsFound: number | null;
  summary: string | null;
  correctiveActions: string | null;
  /** A NC onde as ações viraram itens de CAPA (FDS-004-A); nula quando o simulado não gerou ação. */
  nonConformityId: string | null;
  /** Tempo da cervejaria, não do sistema — é o que a norma cobra. */
  elapsedSeconds: number;
}

/** Ação corretiva do simulado, com o que a distingue de uma intenção: tipo, dono e prazo. */
export interface DrillCapaAction {
  kind: 'CORRECTIVE' | 'PREVENTIVE';
  description: string;
  owner: string;
  dueOn: string;
}

export interface DrillDestination {
  reference: string;
  destination: string;
  contact: string | null;
  units: number;
}

export interface DrillReport {
  drill: RecallDrill;
  unitsInScope: number;
  destinationsReached: number;
  destinations: DrillDestination[];
  gaps: LineageGap[];
  /** Lacunas viradas do avesso: o que fazer para a cobertura ser maior da próxima vez. */
  findings: string[];
}
