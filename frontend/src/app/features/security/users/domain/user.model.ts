export type AccountStatus = 'INVITED' | 'ACTIVE' | 'LOCKED' | 'DISABLED';

export interface SecurityUserSummary {
  id: string;
  email: string;
  displayName: string;
  status: AccountStatus;
  emailVerifiedAt: string | null;
}

export interface InviteUserRequest {
  email: string;
  displayName: string;
}
