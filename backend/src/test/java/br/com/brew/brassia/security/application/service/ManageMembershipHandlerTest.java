package br.com.brew.brassia.security.application.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import br.com.brew.brassia.audit.AuditEvent;
import br.com.brew.brassia.audit.AuditTrail;
import br.com.brew.brassia.security.application.port.inbound.ManageMembershipUseCase.Command;
import br.com.brew.brassia.security.application.port.outbound.GroupMembershipRepository;
import br.com.brew.brassia.security.application.port.outbound.SecurityUserRepository;
import br.com.brew.brassia.security.domain.AccountStatus;
import br.com.brew.brassia.security.domain.DisplayName;
import br.com.brew.brassia.security.domain.EmailAddress;
import br.com.brew.brassia.security.domain.SecurityUser;
import br.com.brew.brassia.security.domain.UserId;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import br.com.brew.brassia.security.application.port.outbound.EffectivePermissionsRepository;
import br.com.brew.brassia.security.application.port.outbound.GroupPermissionRepository;
import br.com.brew.brassia.security.application.port.outbound.SegregationRuleRepository;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class ManageMembershipHandlerTest {

    private final List<AuditEvent> audited = new ArrayList<>();
    private final AuditTrail audit = audited::add;
    private final FakeUsers users = new FakeUsers();
    private final FakeMemberships memberships = new FakeMemberships();

    private ManageMembershipHandler handler() {
        SegregationChecker segregation = new SegregationChecker(
                new SegregationRuleRepository() {
                    @Override public UUID create(UUID breweryId, String left, String right, String reason) { return null; }
                    @Override public java.util.List<RuleView> listActive(UUID breweryId) { return List.of(); }
                    @Override public java.util.Optional<RuleView> findById(UUID id) { return java.util.Optional.empty(); }
                },
                (userId, breweryId) -> Set.of(),
                groupId -> Set.of());
        return new ManageMembershipHandler(users, memberships, segregation, audit);
    }

    private final UUID brewery = UUID.randomUUID();

    private Command command(UUID targetUserId, UUID groupId) {
        return new Command(UUID.randomUUID(), brewery, targetUserId, groupId);
    }

    private UserId storedUser() {
        var user = SecurityUser.reconstitute(UserId.newId(), new EmailAddress("u@x.com"),
                new DisplayName("U"), AccountStatus.ACTIVE, Instant.now(), 1);
        users.store.add(user);
        return user.id();
    }

    @Test
    void grantAddsMembershipAndAudits() {
        var userId = storedUser();
        var groupId = memberships.activeGroup();

        handler().grant(command(userId.value(), groupId));

        assertThat(memberships.added).hasSize(1);
        assertThat(audited).singleElement().satisfies(e -> assertThat(e.action()).isEqualTo("security.membership.grant"));
    }

    @Test
    void grantRejectsDuplicate() {
        var userId = storedUser();
        var groupId = memberships.activeGroup();
        handler().grant(command(userId.value(), groupId));

        assertThatThrownBy(() -> handler().grant(command(userId.value(), groupId)))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void grantRejectsInactiveGroup() {
        var userId = storedUser();

        assertThatThrownBy(() -> handler().grant(command(userId.value(), UUID.randomUUID())))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void grantRejectsUnknownUser() {
        var groupId = memberships.activeGroup();

        assertThatThrownBy(() -> handler().grant(command(UUID.randomUUID(), groupId)))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void revokeAudits() {
        var userId = storedUser();
        var groupId = memberships.activeGroup();
        handler().grant(command(userId.value(), groupId));
        audited.clear();

        handler().revoke(command(userId.value(), groupId));

        assertThat(audited).singleElement().satisfies(e -> assertThat(e.action()).isEqualTo("security.membership.revoke"));
    }

    private static final class FakeUsers implements SecurityUserRepository {
        final List<SecurityUser> store = new ArrayList<>();

        @Override public boolean existsByNormalizedEmail(String e) { return false; }
        @Override public Optional<SecurityUser> findByNormalizedEmail(String e) { return Optional.empty(); }
        @Override public Optional<SecurityUser> findById(UserId id) {
            return store.stream().filter(u -> u.id().equals(id)).findFirst();
        }
        @Override public List<SecurityUser> findPage(int p, int s) { return List.copyOf(store); }
        @Override public long count() { return store.size(); }
        @Override public void save(SecurityUser user) { store.add(user); }
    }

    private static final class FakeMemberships implements GroupMembershipRepository {
        private final UUID group = UUID.randomUUID();
        final List<String> added = new ArrayList<>();

        UUID activeGroup() { return group; }

        @Override public Optional<UUID> groupIdByCode(String code) { return Optional.of(group); }
        @Override public boolean groupActiveById(UUID groupId) { return group.equals(groupId); }
        @Override public boolean hasActiveMembership(UserId u, UUID g, UUID b) { return added.contains(key(u, g, b)); }
        @Override public void addMembership(UserId u, UUID g, UUID b) { added.add(key(u, g, b)); }
        @Override public void revokeMembership(UserId u, UUID g, UUID b) { added.remove(key(u, g, b)); }
        @Override public List<MembershipRecord> listActiveByBrewery(UUID breweryId) { return List.of(); }

        private static String key(UserId u, UUID g, UUID b) { return u.value() + "/" + g + "/" + b; }
    }
}
