export interface BreweryRef {
  id: string;
  code: string;
  name: string;
}

export interface SessionUser {
  userId: string;
  displayName: string;
  /** Cervejaria ativa; nula até haver alguma acessível. */
  activeBrewery: BreweryRef | null;
  accessibleBreweries: BreweryRef[];
  permissions: string[];
}

export interface LoginRequest {
  email: string;
  password: string;
}

export type MfaMethod = 'TOTP' | 'RECOVERY_CODE';

/** Resposta do login quando a conta exige um segundo fator. */
export interface MfaRequired {
  status: 'MFA_REQUIRED';
  methods: MfaMethod[];
}

/** O login pode concluir a sessão ou pedir o segundo fator. */
export type LoginResult = SessionUser | MfaRequired;

export function isMfaRequired(result: LoginResult): result is MfaRequired {
  return (result as MfaRequired).status === 'MFA_REQUIRED';
}

export interface MfaLoginRequest {
  code: string;
  method: MfaMethod;
}
