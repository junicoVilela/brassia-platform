package br.com.brew.brassia.security.application.port.outbound;

import br.com.brew.brassia.security.domain.SecurityUser;
import br.com.brew.brassia.security.domain.UserId;
import java.util.Optional;

public interface SecurityUserRepository {
    boolean existsByNormalizedEmail(String normalizedEmail);
    Optional<SecurityUser> findById(UserId id);
    void save(SecurityUser user);
}
