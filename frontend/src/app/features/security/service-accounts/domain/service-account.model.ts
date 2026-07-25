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
