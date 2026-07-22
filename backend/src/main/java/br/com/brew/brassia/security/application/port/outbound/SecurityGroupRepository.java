package br.com.brew.brassia.security.application.port.outbound;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/** Persistência de grupos RBAC e vínculo com permissões. */
public interface SecurityGroupRepository {
    boolean existsByCode(UUID breweryId, String code);

    Optional<GroupRecord> findById(UUID id);

    UUID insert(NewGroup group);

    /** Atualiza metadados com optimistic locking; retorna false se a versão divergiu. */
    boolean update(UUID id, String name, String description, long expectedVersion);

    void replacePermissions(UUID groupId, List<UUID> permissionIds);

    /**
     * Resolve códigos ativos para ids. Lança {@link IllegalArgumentException} se algum
     * código for inexistente ou inativo.
     */
    List<UUID> resolveActivePermissionIds(List<String> codes);

    List<String> permissionCodesOf(UUID groupId);

    record NewGroup(UUID breweryId, String code, String name, String description) {}

    record GroupRecord(
            UUID id,
            UUID breweryId,
            String code,
            String name,
            String description,
            boolean systemGroup,
            boolean active,
            long version) {}
}
