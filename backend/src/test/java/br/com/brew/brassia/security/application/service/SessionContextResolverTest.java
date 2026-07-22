package br.com.brew.brassia.security.application.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import br.com.brew.brassia.brewery.BreweryDirectory;
import br.com.brew.brassia.brewery.BreweryRef;
import br.com.brew.brassia.security.application.port.outbound.BreweryAccessRepository;
import br.com.brew.brassia.security.application.port.outbound.EffectivePermissionsRepository;
import br.com.brew.brassia.security.domain.UserId;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import br.com.brew.brassia.shared.security.ForbiddenException;
import org.junit.jupiter.api.Test;

class SessionContextResolverTest {

    private final BreweryRef alpha = new BreweryRef(UUID.randomUUID(), "ALPHA", "Alpha");
    private final BreweryRef beta = new BreweryRef(UUID.randomUUID(), "BETA", "Beta");
    private final UserId userId = UserId.newId();

    private SessionContextResolver resolver(boolean global, List<UUID> scoped, Set<String> perms) {
        BreweryAccessRepository access = new BreweryAccessRepository() {
            @Override public boolean hasGlobalMembership(UserId u) { return global; }
            @Override public List<UUID> scopedBreweryIds(UserId u) { return scoped; }
        };
        BreweryDirectory directory = new BreweryDirectory() {
            @Override public List<BreweryRef> findAll() { return List.of(beta, alpha); }
            @Override public Optional<BreweryRef> findById(UUID id) {
                return List.of(alpha, beta).stream().filter(b -> b.id().equals(id)).findFirst();
            }
        };
        EffectivePermissionsRepository permissions = (u, b) -> perms;
        return new SessionContextResolver(access, directory, permissions);
    }

    @Test
    void globalMembershipSeesAllBreweriesSortedByCodeAndDefaultsToFirst() {
        var ctx = resolver(true, List.of(), Set.of("security.user.read")).resolve(userId, null);

        assertThat(ctx.accessibleBreweries()).extracting(BreweryRef::code).containsExactly("ALPHA", "BETA");
        assertThat(ctx.activeBreweryId()).isEqualTo(alpha.id()); // primeira por código
        assertThat(ctx.permissions()).containsExactly("security.user.read");
    }

    @Test
    void scopedMembershipSeesOnlyItsBreweries() {
        var ctx = resolver(false, List.of(beta.id()), Set.of()).resolve(userId, null);

        assertThat(ctx.accessibleBreweries()).extracting(BreweryRef::code).containsExactly("BETA");
        assertThat(ctx.activeBreweryId()).isEqualTo(beta.id());
    }

    @Test
    void requestedActiveMustBeAccessible() {
        var resolver = resolver(false, List.of(beta.id()), Set.of());

        assertThat(resolver.resolve(userId, beta.id()).activeBreweryId()).isEqualTo(beta.id());
        assertThatThrownBy(() -> resolver.resolve(userId, alpha.id()))
                .isInstanceOf(ForbiddenException.class);
    }

    @Test
    void noAccessibleBreweriesYieldsNullActive() {
        var ctx = resolver(false, List.of(), Set.of()).resolve(userId, null);
        assertThat(ctx.activeBreweryId()).isNull();
        assertThat(ctx.accessibleBreweries()).isEmpty();
    }
}
