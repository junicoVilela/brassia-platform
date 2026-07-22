package br.com.brew.brassia.security.application.port.outbound;

import br.com.brew.brassia.security.domain.UserId;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/** Associação de usuários a grupos de segurança (opcionalmente escopada a cervejaria). */
public interface GroupMembershipRepository {
    record MembershipRecord(UUID userId, UUID groupId) {}

    Optional<UUID> groupIdByCode(String code);
    boolean groupActiveById(UUID groupId);
    boolean hasActiveMembership(UserId userId, UUID groupId, UUID breweryId);
    void addMembership(UserId userId, UUID groupId, UUID breweryId);
    void revokeMembership(UserId userId, UUID groupId, UUID breweryId);
    List<MembershipRecord> listActiveByBrewery(UUID breweryId);
}
