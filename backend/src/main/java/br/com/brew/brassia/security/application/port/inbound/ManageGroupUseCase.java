package br.com.brew.brassia.security.application.port.inbound;

import java.util.List;
import java.util.Set;
import java.util.UUID;

/** Cria e atualiza grupos customizados (não de sistema) na cervejaria ativa. */
public interface ManageGroupUseCase {
    Result create(CreateCommand command);

    Result update(UpdateCommand command);

    record CreateCommand(
            UUID actorId,
            UUID breweryId,
            Set<String> actorPermissions,
            String code,
            String name,
            String description,
            List<String> permissionCodes) {}

    record UpdateCommand(
            UUID actorId,
            UUID breweryId,
            Set<String> actorPermissions,
            UUID groupId,
            String name,
            String description,
            List<String> permissionCodes,
            long version) {}

    record Result(
            UUID id,
            String code,
            String name,
            String description,
            UUID breweryId,
            boolean systemGroup,
            boolean active,
            long version,
            List<String> permissions) {}
}
