package br.com.brew.brassia.security.application.port.inbound;

import java.util.Map;
import java.util.UUID;

public interface ScimProvisioningUseCase {
    Map<String, Object> createUser(CreateUserCommand command);
    Map<String, Object> getUser(GetUserCommand command);
    void patchUser(PatchUserCommand command);
    void deleteUser(DeleteUserCommand command);
    Map<String, Object> createGroup(CreateGroupCommand command);
    Map<String, Object> getGroup(GetGroupCommand command);

    record CreateUserCommand(UUID breweryId, UUID providerId, String externalId, String userName,
            String displayName, boolean active, String idempotencyKey, String traceId) {}
    record GetUserCommand(UUID userId) {}
    record PatchUserCommand(UUID userId, boolean active, String idempotencyKey, String traceId) {}
    record DeleteUserCommand(UUID userId, String idempotencyKey, String traceId) {}
    record CreateGroupCommand(UUID providerId, String externalGroupId, String displayName, String idempotencyKey) {}
    record GetGroupCommand(UUID providerId, String externalGroupId) {}
}
