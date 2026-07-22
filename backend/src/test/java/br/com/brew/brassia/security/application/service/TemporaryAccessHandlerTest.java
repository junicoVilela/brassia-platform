package br.com.brew.brassia.security.application.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import br.com.brew.brassia.audit.AuditEvent;
import br.com.brew.brassia.audit.AuditTrail;
import br.com.brew.brassia.security.application.port.inbound.TemporaryAccessUseCase.RequestCommand;
import br.com.brew.brassia.security.application.port.outbound.SecurityUserRepository;
import br.com.brew.brassia.security.application.port.outbound.TemporaryAccessRepository;
import br.com.brew.brassia.security.domain.AccountStatus;
import br.com.brew.brassia.security.domain.DisplayName;
import br.com.brew.brassia.security.domain.EmailAddress;
import br.com.brew.brassia.security.domain.SecurityUser;
import br.com.brew.brassia.security.domain.TemporaryAccessGrant;
import br.com.brew.brassia.security.domain.UserId;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import br.com.brew.brassia.shared.security.ForbiddenException;
import org.junit.jupiter.api.Test;

class TemporaryAccessHandlerTest {

    private final List<AuditEvent> audited = new ArrayList<>();
    private final AuditTrail audit = audited::add;
    private final FakeUsers users = new FakeUsers();
    private final FakeGrants grants = new FakeGrants();
    private final UUID brewery = UUID.randomUUID();

    private TemporaryAccessHandler handler() {
        return new TemporaryAccessHandler(grants, users, audit);
    }

    private RequestCommand request(UUID actor, UUID target, String permission) {
        return new RequestCommand(actor, brewery, target, permission, "preciso por 4h", 4);
    }

    @Test
    void requestCommonPermissionInsertsAndAudits() {
        var target = users.store();
        var id = handler().request(request(UUID.randomUUID(), target, "common.perm"));

        assertThat(grants.store).containsKey(id);
        assertThat(grants.store.get(id).isApproved()).isFalse();
        assertThat(audited).singleElement()
                .satisfies(e -> assertThat(e.action()).isEqualTo("security.temporary-access.request"));
    }

    @Test
    void requestRejectsUnknownPermission() {
        var target = users.store();
        assertThatThrownBy(() -> handler().request(request(UUID.randomUUID(), target, "ghost.perm")))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void requestRejectsUnknownUser() {
        assertThatThrownBy(() -> handler().request(request(UUID.randomUUID(), UUID.randomUUID(), "common.perm")))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void approveBySecondUserSetsApproverAndAudits() {
        var target = users.store();
        var requester = UUID.randomUUID();
        var id = handler().request(request(requester, target, "crit.perm"));
        audited.clear();

        handler().approve(id, UUID.randomUUID(), brewery);

        assertThat(grants.store.get(id).isApproved()).isTrue();
        assertThat(audited).singleElement()
                .satisfies(e -> assertThat(e.action()).isEqualTo("security.temporary-access.approve"));
    }

    @Test
    void approveByRequesterIsForbidden() {
        var target = users.store();
        var requester = UUID.randomUUID();
        var id = handler().request(request(requester, target, "crit.perm"));

        assertThatThrownBy(() -> handler().approve(id, requester, brewery))
                .isInstanceOf(ForbiddenException.class);
    }

    @Test
    void approveAlreadyApprovedConflicts() {
        var target = users.store();
        var id = handler().request(request(UUID.randomUUID(), target, "crit.perm"));
        handler().approve(id, UUID.randomUUID(), brewery);

        assertThatThrownBy(() -> handler().approve(id, UUID.randomUUID(), brewery))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void approveOnOtherBreweryNotFound() {
        var target = users.store();
        var id = handler().request(request(UUID.randomUUID(), target, "crit.perm"));

        assertThatThrownBy(() -> handler().approve(id, UUID.randomUUID(), UUID.randomUUID()))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void revokeAuditsAndSecondRevokeConflicts() {
        var target = users.store();
        var id = handler().request(request(UUID.randomUUID(), target, "common.perm"));
        audited.clear();

        handler().revoke(id, UUID.randomUUID(), brewery);
        assertThat(grants.store.get(id).isRevoked()).isTrue();
        assertThat(audited).singleElement()
                .satisfies(e -> assertThat(e.action()).isEqualTo("security.temporary-access.revoke"));

        assertThatThrownBy(() -> handler().revoke(id, UUID.randomUUID(), brewery))
                .isInstanceOf(IllegalStateException.class);
    }

    private static final class FakeUsers implements SecurityUserRepository {
        final List<SecurityUser> users = new ArrayList<>();

        UUID store() {
            var user = SecurityUser.reconstitute(UserId.newId(), new EmailAddress("u@x.com"),
                    new DisplayName("U"), AccountStatus.ACTIVE, Instant.now(), 1);
            users.add(user);
            return user.id().value();
        }

        @Override public boolean existsByNormalizedEmail(String e) { return false; }
        @Override public Optional<SecurityUser> findByNormalizedEmail(String e) { return Optional.empty(); }
        @Override public Optional<SecurityUser> findById(UserId id) {
            return users.stream().filter(u -> u.id().equals(id)).findFirst();
        }
        @Override public List<SecurityUser> findPage(int p, int s) { return List.copyOf(users); }
        @Override public long count() { return users.size(); }
        @Override public void save(SecurityUser user) { users.add(user); }
    }

    /** Repositório em memória com duas permissões: comum e crítica. */
    private static final class FakeGrants implements TemporaryAccessRepository {
        final Map<UUID, TemporaryAccessGrant> store = new LinkedHashMap<>();
        private final Map<String, PermissionRef> catalog = new HashMap<>();
        private final Map<UUID, String> codeById = new HashMap<>();
        private final Map<UUID, Boolean> criticalById = new HashMap<>();

        FakeGrants() {
            register("common.perm", false);
            register("crit.perm", true);
        }

        private void register(String code, boolean critical) {
            var ref = new PermissionRef(UUID.randomUUID(), critical);
            catalog.put(code, ref);
            codeById.put(ref.id(), code);
            criticalById.put(ref.id(), critical);
        }

        @Override public Optional<PermissionRef> permissionByCode(String code) {
            return Optional.ofNullable(catalog.get(code));
        }

        @Override public UUID insert(NewGrant g) {
            var id = UUID.randomUUID();
            store.put(id, new TemporaryAccessGrant(id, g.breweryId(), g.userId(), g.permissionId(),
                    codeById.get(g.permissionId()), criticalById.get(g.permissionId()), g.reason(),
                    g.validFrom(), g.validUntil(), g.requestedBy(), null, null));
            return id;
        }

        @Override public Optional<TemporaryAccessGrant> findById(UUID id, UUID breweryId) {
            return Optional.ofNullable(store.get(id)).filter(g -> g.breweryId().equals(breweryId));
        }

        @Override public void approve(UUID id, UUID approverId, Instant approvedAt) {
            var g = store.get(id);
            store.put(id, new TemporaryAccessGrant(g.id(), g.breweryId(), g.userId(), g.permissionId(),
                    g.permissionCode(), g.permissionCritical(), g.reason(), g.validFrom(), g.validUntil(),
                    g.requestedBy(), approverId, g.revokedAt()));
        }

        @Override public void revoke(UUID id, UUID revokedBy, Instant revokedAt) {
            var g = store.get(id);
            store.put(id, new TemporaryAccessGrant(g.id(), g.breweryId(), g.userId(), g.permissionId(),
                    g.permissionCode(), g.permissionCritical(), g.reason(), g.validFrom(), g.validUntil(),
                    g.requestedBy(), g.approvedBy(), revokedAt));
        }

        @Override public List<TemporaryAccessGrant> current(UUID breweryId) {
            return store.values().stream().filter(g -> g.breweryId().equals(breweryId)).toList();
        }
    }
}
