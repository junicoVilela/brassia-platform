package br.com.brew.brassia.security.application.port.outbound;

import java.util.Optional;
import java.util.UUID;

/** Porta legada LDAP — sem bind real nesta fatia (SEC-016). */
public interface LdapDirectoryPort {
    Optional<String> resolveUserDn(String username);
}
