/** Provedor de federação (subconjunto de ProviderView). */
export interface FederationProvider {
  id: string;
  code: string;
  displayName: string;
  protocol: string;
  status: string;
  issuerOrEntityId: string;
  metadataUri: string | null;
  jitMode: boolean;
  version: number;
}

export type FederationProtocol = 'SAML' | 'OIDC';

export interface CreateFederationProvider {
  code: string;
  displayName: string;
  protocol: FederationProtocol;
  issuerOrEntityId: string;
  configuration: Record<string, unknown>;
}

/** Identidade externa vinculada a um provedor (SEC-B06). */
export interface ExternalIdentity {
  userId: string;
  externalSubject: string;
  normalizedEmail: string | null;
  linkedAt: string;
}
