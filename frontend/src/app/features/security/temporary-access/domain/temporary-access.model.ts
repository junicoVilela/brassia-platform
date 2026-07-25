/** Concessão de acesso temporário (espelha GrantView do backend). */
export interface TemporaryGrant {
  id: string;
  userId: string;
  permissionCode: string;
  critical: boolean;
  reason: string;
  validFrom: string;
  validUntil: string;
  requestedBy: string;
  approvedBy: string | null;
  status: string;
}

export interface RequestGrant {
  userId: string;
  permissionCode: string;
  reason: string;
  durationHours: number;
}

/** Opções para os selects (usuário-alvo e permissão). */
export interface UserOption {
  id: string;
  displayName: string;
}

export interface PermissionOption {
  code: string;
  name: string;
  critical: boolean;
}
