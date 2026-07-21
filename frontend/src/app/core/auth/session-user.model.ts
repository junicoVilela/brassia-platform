export interface SessionUser {
  userId: string;
  displayName: string;
  /** Nula até a seleção de cervejaria ativa (SEC-005). */
  brewery: string | null;
  permissions: string[];
}

export interface LoginRequest {
  email: string;
  password: string;
}
