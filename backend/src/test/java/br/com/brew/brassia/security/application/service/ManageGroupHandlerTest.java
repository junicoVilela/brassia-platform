package br.com.brew.brassia.security.application.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import br.com.brew.brassia.audit.AuditEvent;
import br.com.brew.brassia.audit.AuditTrail;
import br.com.brew.brassia.security.application.port.inbound.ManageGroupUseCase.CreateCommand;
import br.com.brew.brassia.security.application.port.inbound.ManageGroupUseCase.UpdateCommand;
import br.com.brew.brassia.security.application.port.outbound.SecurityGroupRepository;
import br.com.brew.brassia.shared.security.ForbiddenException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class ManageGroupHandlerTest {

    private final List<AuditEvent> audited = new ArrayList<>();
    private final AuditTrail audit = audited::add;
    private final FakeGroups groups = new FakeGroups();
    private final UUID brewery = UUID.randomUUID();
    private final UUID actor = UUID.randomUUID();
    private final Set<String> actorPerms = Set.of("recipe.create", "security.user.read", "security.group.manage");

    private ManageGroupHandler handler() {
        return new ManageGroupHandler(groups, audit);
    }

    @Test
    void createCustomGroupWithAssignablePermissions() {
        var result = handler().create(new CreateCommand(
                actor, brewery, actorPerms, "brewers", "Cervejeiros", "dia a dia",
                List.of("recipe.create", "security.user.read")));

        assertThat(result.code()).isEqualTo("BREWERS");
        assertThat(result.breweryId()).isEqualTo(brewery);
        assertThat(result.systemGroup()).isFalse();
        assertThat(result.permissions()).containsExactly("recipe.create", "security.user.read");
        assertThat(audited).singleElement()
                .satisfies(e -> assertThat(e.action()).isEqualTo("security.group.create"));
    }

    @Test
    void createRejectsPermissionActorDoesNotHave() {
        assertThatThrownBy(() -> handler().create(new CreateCommand(
                actor, brewery, actorPerms, "OPS", "Ops", null,
                List.of("security.user.disable"))))
                .isInstanceOf(ForbiddenException.class);
    }

    @Test
    void createRejectsDuplicateCode() {
        handler().create(new CreateCommand(actor, brewery, actorPerms, "BREWERS", "A", null, List.of()));
        assertThatThrownBy(() -> handler().create(new CreateCommand(
                actor, brewery, actorPerms, "brewers", "B", null, List.of())))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void updateReplacesPermissionsAndBumpsVersion() {
        var created = handler().create(new CreateCommand(
                actor, brewery, actorPerms, "BREWERS", "Cervejeiros", null, List.of("recipe.create")));
        audited.clear();

        var updated = handler().update(new UpdateCommand(
                actor, brewery, actorPerms, created.id(), "Cervejeiros Senior", "desc",
                List.of("recipe.create", "security.user.read"), created.version()));

        assertThat(updated.name()).isEqualTo("Cervejeiros Senior");
        assertThat(updated.version()).isEqualTo(created.version() + 1);
        assertThat(updated.permissions()).containsExactly("recipe.create", "security.user.read");
        assertThat(audited).singleElement()
                .satisfies(e -> assertThat(e.action()).isEqualTo("security.group.update"));
    }

    @Test
    void updateSystemGroupIsForbidden() {
        var systemId = groups.insertSystem();
        assertThatThrownBy(() -> handler().update(new UpdateCommand(
                actor, brewery, actorPerms, systemId, "X", null, List.of(), 0)))
                .isInstanceOf(ForbiddenException.class);
    }

    @Test
    void updateOtherBreweryIsForbidden() {
        var created = handler().create(new CreateCommand(
                actor, brewery, actorPerms, "BREWERS", "Cervejeiros", null, List.of()));
        assertThatThrownBy(() -> handler().update(new UpdateCommand(
                actor, UUID.randomUUID(), actorPerms, created.id(), "X", null, List.of(), created.version())))
                .isInstanceOf(ForbiddenException.class);
    }

    @Test
    void updateStaleVersionConflicts() {
        var created = handler().create(new CreateCommand(
                actor, brewery, actorPerms, "BREWERS", "Cervejeiros", null, List.of()));
        assertThatThrownBy(() -> handler().update(new UpdateCommand(
                actor, brewery, actorPerms, created.id(), "X", null, List.of(), created.version() + 5)))
                .isInstanceOf(IllegalStateException.class);
    }

    private static final class FakeGroups implements SecurityGroupRepository {
        private final Map<UUID, GroupRecord> store = new LinkedHashMap<>();
        private final Map<UUID, List<String>> permissions = new HashMap<>();
        private final Map<String, UUID> catalog = Map.of(
                "recipe.create", UUID.randomUUID(),
                "security.user.read", UUID.randomUUID(),
                "security.user.disable", UUID.randomUUID(),
                "security.group.manage", UUID.randomUUID());

        UUID insertSystem() {
            var id = UUID.randomUUID();
            store.put(id, new GroupRecord(id, null, "ADMINISTRATORS", "Administradores", null, true, true, 0));
            permissions.put(id, List.of());
            return id;
        }

        @Override
        public boolean existsByCode(UUID breweryId, String code) {
            return store.values().stream()
                    .anyMatch(g -> g.code().equals(code)
                            && java.util.Objects.equals(g.breweryId(), breweryId));
        }

        @Override
        public Optional<GroupRecord> findById(UUID id) {
            return Optional.ofNullable(store.get(id));
        }

        @Override
        public UUID insert(NewGroup group) {
            var id = UUID.randomUUID();
            store.put(id, new GroupRecord(id, group.breweryId(), group.code(), group.name(),
                    group.description(), false, true, 0));
            permissions.put(id, List.of());
            return id;
        }

        @Override
        public boolean update(UUID id, String name, String description, long expectedVersion) {
            var current = store.get(id);
            if (current == null || current.version() != expectedVersion || current.systemGroup() || !current.active()) {
                return false;
            }
            store.put(id, new GroupRecord(id, current.breweryId(), current.code(), name, description,
                    false, true, current.version() + 1));
            return true;
        }

        @Override
        public void replacePermissions(UUID groupId, List<UUID> permissionIds) {
            var byId = new HashMap<UUID, String>();
            catalog.forEach((code, pid) -> byId.put(pid, code));
            permissions.put(groupId, permissionIds.stream().map(byId::get).toList());
        }

        @Override
        public List<UUID> resolveActivePermissionIds(List<String> codes) {
            List<UUID> ids = new ArrayList<>();
            for (String code : codes) {
                var id = catalog.get(code);
                if (id == null) {
                    throw new IllegalArgumentException("permissão inexistente ou inativa: " + code);
                }
                ids.add(id);
            }
            return ids;
        }

        @Override
        public List<String> permissionCodesOf(UUID groupId) {
            return permissions.getOrDefault(groupId, List.of());
        }
    }
}
