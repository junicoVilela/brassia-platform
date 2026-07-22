package br.com.brew.brassia.security.application.service;

import br.com.brew.brassia.security.application.port.inbound.AdministerAccountUseCase;
import br.com.brew.brassia.security.application.port.inbound.InviteUserUseCase;
import br.com.brew.brassia.security.application.port.inbound.ScimProvisioningUseCase;
import br.com.brew.brassia.security.application.port.outbound.ProvisioningEventRepository;
import br.com.brew.brassia.security.application.port.outbound.ScimGroupMappingRepository;
import br.com.brew.brassia.security.application.port.outbound.SecurityUserRepository;
import br.com.brew.brassia.security.domain.DisplayName;
import br.com.brew.brassia.security.domain.EmailAddress;
import br.com.brew.brassia.security.domain.SecurityUser;
import br.com.brew.brassia.security.domain.UserId;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

/** Provisionamento SCIM mínimo (SEC-016). */
public final class ScimProvisioningHandler {
    private final SecurityUserRepository users;
    private final ProvisioningEventRepository events;
    private final ScimGroupMappingRepository groupMappings;
    private final AdministerAccountUseCase administerAccount;

    public ScimProvisioningHandler(
            SecurityUserRepository users,
            ProvisioningEventRepository events,
            ScimGroupMappingRepository groupMappings,
            AdministerAccountUseCase administerAccount) {
        this.users = Objects.requireNonNull(users);
        this.events = Objects.requireNonNull(events);
        this.groupMappings = Objects.requireNonNull(groupMappings);
        this.administerAccount = Objects.requireNonNull(administerAccount);
    }

    public Map<String, Object> createUser(ScimProvisioningUseCase.CreateUserCommand command) {
        if (command.idempotencyKey() != null && events.existsByIdempotencyKey(command.idempotencyKey())) {
            events.log(command.providerId(), command.externalId(), "USER", "CREATE", "NOOP",
                    command.idempotencyKey(), null, command.traceId());
            return userResource(command.externalId(), null, command.userName(), command.displayName(), command.active());
        }
        var email = new EmailAddress(command.userName());
        SecurityUser user;
        if (users.existsByNormalizedEmail(email.normalized())) {
            user = users.findByNormalizedEmail(email.normalized()).orElseThrow();
        } else if (command.active()) {
            user = SecurityUser.activeAccount(email, new DisplayName(command.displayName()), Instant.now());
            users.save(user);
        } else {
            user = SecurityUser.invite(email, new DisplayName(command.displayName()));
            users.save(user);
        }
        events.log(command.providerId(), command.externalId(), "USER", "CREATE", "SUCCESS",
                command.idempotencyKey(), null, command.traceId());
        return userResource(command.externalId(), user.id().value(), command.userName(), command.displayName(), command.active());
    }

    public Map<String, Object> getUser(ScimProvisioningUseCase.GetUserCommand command) {
        var user = users.findById(new UserId(command.userId())).orElseThrow();
        return userResource(user.id().value().toString(), user.id().value(), user.email().value(),
                user.displayName().value(), user.status().name().equals("ACTIVE"));
    }

    public void patchUser(ScimProvisioningUseCase.PatchUserCommand command) {
        if (!command.active()) {
            administerAccount.handle(new AdministerAccountUseCase.Command(
                    command.userId(), null, command.userId(), AdministerAccountUseCase.Operation.DISABLE));
        }
        events.log(null, command.userId().toString(), "USER", "PATCH", "SUCCESS",
                command.idempotencyKey(), null, command.traceId());
    }

    public void deleteUser(ScimProvisioningUseCase.DeleteUserCommand command) {
        administerAccount.handle(new AdministerAccountUseCase.Command(
                command.userId(), null, command.userId(), AdministerAccountUseCase.Operation.DISABLE));
        events.log(null, command.userId().toString(), "USER", "DELETE", "SUCCESS",
                command.idempotencyKey(), null, command.traceId());
    }

    public Map<String, Object> createGroup(ScimProvisioningUseCase.CreateGroupCommand command) {
        var mapping = groupMappings.findActive(command.providerId(), command.externalGroupId());
        if (mapping.isEmpty()) {
            throw new IllegalArgumentException("grupo externo não allowlisted");
        }
        events.log(command.providerId(), command.externalGroupId(), "GROUP", "CREATE", "SUCCESS",
                command.idempotencyKey(), null, null);
        return Map.of("id", command.externalGroupId(), "displayName", command.displayName());
    }

    public Map<String, Object> getGroup(ScimProvisioningUseCase.GetGroupCommand command) {
        var mapping = groupMappings.findActive(command.providerId(), command.externalGroupId())
                .orElseThrow(() -> new IllegalArgumentException("grupo não mapeado"));
        return Map.of("id", command.externalGroupId(), "securityGroupId", mapping.securityGroupId().toString());
    }

    private static Map<String, Object> userResource(String externalId, UUID id, String userName,
            String displayName, boolean active) {
        var body = new HashMap<String, Object>();
        body.put("schemas", new String[] {"urn:ietf:params:scim:schemas:core:2.0:User"});
        if (id != null) {
            body.put("id", id.toString());
        }
        body.put("externalId", externalId);
        body.put("userName", userName);
        body.put("displayName", displayName);
        body.put("active", active);
        return body;
    }
}
