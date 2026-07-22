package br.com.brew.brassia.security.adapter.outbound.ldap;

import br.com.brew.brassia.security.application.port.outbound.LdapDirectoryPort;
import java.util.Optional;
import org.springframework.stereotype.Component;

/** Stub LDAP/AD — integração real em sprint futura. */
@Component
final class StubLdapDirectoryPort implements LdapDirectoryPort {
    @Override
    public Optional<String> resolveUserDn(String username) {
        return Optional.empty();
    }
}
