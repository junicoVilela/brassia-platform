package br.com.brew.brassia.shared.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.security.access.AccessDeniedException;

class SecurityPrincipalTest {

    @Test
    void identityOnlyHasNoBreweryNorPermissions() {
        var principal = SecurityPrincipal.identityOnly(UUID.randomUUID(), "Brewer");

        assertThat(principal.breweryId()).isNull();
        assertThat(principal.permissions()).isEmpty();
        assertThatThrownBy(() -> principal.requirePermission("security.user.read"))
                .isInstanceOf(AccessDeniedException.class);
    }

    @Test
    void requirePermissionPassesWhenGranted() {
        var principal = new SecurityPrincipal(UUID.randomUUID(), UUID.randomUUID(), "Admin",
                Set.of("security.user.read"));

        principal.requirePermission("security.user.read"); // não lança
        assertThat(principal.permissions()).contains("security.user.read");
    }
}
