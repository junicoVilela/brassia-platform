/** Conta de serviço (espelha ServiceAccountResponse). */
export interface ServiceAccount {
  id: string;
  code: string;
  active: boolean;
}

export interface CreateServiceAccount {
  code: string;
  name: string;
}

/** Credencial persistida (metadados; sem segredo) — vinda do GID de credenciais. */
export interface ServiceAccountCredential {
  id: string;
  keyPrefix: string;
  scopes: string[];
  expiresAt: string | null;
  revokedAt: string | null;
  active: boolean;
}

/**
 * Credencial emitida nesta sessão. O `rawKey` (segredo) só é retornado uma vez,
 * na emissão — não é persistido nem relido do backend.
 */
export interface IssuedCredential {
  credentialId: string;
  rawKey: string;
  keyPrefix: string;
  scopes: string[];
  serviceAccountCode: string;
  revoked: boolean;
}
