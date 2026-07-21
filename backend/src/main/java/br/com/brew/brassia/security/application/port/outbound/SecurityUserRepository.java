package br.com.brew.brassia.security.application.port.outbound;

import br.com.brew.brassia.security.domain.SecurityUser;
import br.com.brew.brassia.security.domain.UserId;
import java.util.List;
import java.util.Optional;

public interface SecurityUserRepository {
    boolean existsByNormalizedEmail(String normalizedEmail);
    Optional<SecurityUser> findByNormalizedEmail(String normalizedEmail);
    Optional<SecurityUser> findById(UserId id);
    List<SecurityUser> findPage(int page, int size);
    long count();
    void save(SecurityUser user);
}
