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

/**
 * Monta o request de criação a partir dos valores do form e das permissões
 * selecionadas. Mantém a normalização (descrição vazia -> null) junto do modelo,
 * fora do componente.
 */
export function toCreateGroupRequest(
  value: { code: string; name: string; description: string },
  permissionCodes: Iterable<string>,
): CreateGroupRequest {
  return {
    code: value.code,
    name: value.name,
    description: value.description || null,
    permissionCodes: [...permissionCodes],
  };
}

/** Monta o request de atualização, carregando a versão para o lock otimista. */
export function toUpdateGroupRequest(
  value: { name: string; description: string },
  permissionCodes: Iterable<string>,
  version: number,
): UpdateGroupRequest {
  return {
    name: value.name,
    description: value.description || null,
    permissionCodes: [...permissionCodes],
    version,
  };
}
