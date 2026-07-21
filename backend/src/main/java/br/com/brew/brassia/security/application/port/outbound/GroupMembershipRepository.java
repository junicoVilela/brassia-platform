package br.com.brew.brassia.security.application.port.outbound;

import br.com.brew.brassia.security.domain.UserId;
import java.util.Optional;
import java.util.UUID;

/** Associação de usuários a grupos de segurança. */
public interface GroupMembershipRepository {
    Optional<UUID> groupIdByCode(String code);
    boolean hasMembership(UserId userId, UUID groupId);
    void addMembership(UserId userId, UUID groupId);
}
