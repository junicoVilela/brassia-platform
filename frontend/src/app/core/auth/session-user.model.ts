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
