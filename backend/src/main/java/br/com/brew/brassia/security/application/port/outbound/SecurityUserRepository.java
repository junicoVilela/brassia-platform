package br.com.brew.brassia.security.application.port.outbound;

import br.com.brew.brassia.security.domain.SecurityUser;

public interface SecurityUserRepository {
    boolean existsByNormalizedEmail(String normalizedEmail);
    void save(SecurityUser user);
}
