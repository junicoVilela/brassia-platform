package br.com.brew.brassia.security.adapter.outbound.persistence;

import br.com.brew.brassia.security.application.port.outbound.SecurityUserRepository;
import br.com.brew.brassia.security.domain.SecurityUser;
import br.com.brew.brassia.security.domain.UserId;
import java.util.Optional;
import org.springframework.stereotype.Repository;

// Não pode ser final: @Repository é proxiado (CGLIB) para tradução de exceções.
@Repository
class JpaSecurityUserRepositoryAdapter implements SecurityUserRepository {
    private final SpringDataSecurityUserJpaRepository repository;

    JpaSecurityUserRepositoryAdapter(SpringDataSecurityUserJpaRepository repository) {
        this.repository = repository;
    }

    @Override
    public boolean existsByNormalizedEmail(String normalizedEmail) {
        return repository.existsByNormalizedEmail(normalizedEmail);
    }

    @Override
    public Optional<SecurityUser> findById(UserId id) {
        return repository.findById(id.value()).map(SecurityUserJpaEntity::toDomain);
    }

    @Override
    public void save(SecurityUser user) {
        repository.save(SecurityUserJpaEntity.from(user));
    }
}
