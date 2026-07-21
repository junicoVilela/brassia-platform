package br.com.brew.brassia.security.adapter.outbound.persistence;

import br.com.brew.brassia.security.application.port.outbound.SecurityUserRepository;
import br.com.brew.brassia.security.domain.SecurityUser;
import br.com.brew.brassia.security.domain.UserId;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
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
    public List<SecurityUser> findPage(int page, int size) {
        return repository.findAll(PageRequest.of(page, size, Sort.by("normalizedEmail")))
                .map(SecurityUserJpaEntity::toDomain)
                .getContent();
    }

    @Override
    public long count() {
        return repository.count();
    }

    @Override
    public void save(SecurityUser user) {
        repository.save(SecurityUserJpaEntity.from(user));
    }
}
