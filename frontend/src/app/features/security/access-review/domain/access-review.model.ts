/** Campanha de revisão de acessos (espelha ReviewView). */
export interface AccessReview {
  id: string;
  name: string;
  status: string;
  reviewerId: string;
  dueAt: string;
}

/** Item de uma revisão: uma associação usuário↔grupo a decidir. */
export interface ReviewItem {
  id: string;
  userId: string;
  groupId: string;
  decision: string;
}

export type ReviewDecision = 'KEEP' | 'REMOVE';

/** Regra de segregação de funções (espelha RuleView). */
export interface SegregationRule {
  id: string;
  leftPermissionCode: string;
  rightPermissionCode: string;
  reason: string;
  active: boolean;
}

export interface CreateReview {
  name: string;
  dueAt: string;
}

export interface CreateRule {
  leftPermissionCode: string;
  rightPermissionCode: string;
  reason: string;
}

export interface NamedRef {
  id: string;
  name: string;
}
