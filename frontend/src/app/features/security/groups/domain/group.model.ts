export interface PermissionSummary {
  domain: string;
  code: string;
  name: string;
  critical: boolean;
  active: boolean;
}

export interface GroupSummary {
  id: string;
  code: string;
  name: string;
  description: string | null;
  breweryId: string | null;
  systemGroup: boolean;
  active: boolean;
  version: number;
  permissions: string[];
}

export interface CreateGroupRequest {
  code: string;
  name: string;
  description?: string | null;
  permissionCodes: string[];
}

export interface UpdateGroupRequest {
  name: string;
  description?: string | null;
  permissionCodes: string[];
  version: number;
}
