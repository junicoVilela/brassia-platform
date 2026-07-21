package br.com.brew.brassia.security.adapter.outbound.persistence;

import br.com.brew.brassia.security.application.port.outbound.PasswordCredentialRepository;
import br.com.brew.brassia.security.domain.PasswordCredential;
import br.com.brew.brassia.security.domain.UserId;
import java.util.Optional;
import org.springframework.stereotype.Repository;

// Não pode ser final: @Repository é proxiado (CGLIB) para tradução de exceções.
@Repository
class JpaPasswordCredentialRepositoryAdapter implements PasswordCredentialRepository {
    private final SpringDataPasswordCredentialJpaRepository repository;

    JpaPasswordCredentialRepositoryAdapter(SpringDataPasswordCredentialJpaRepository repository) {
        this.repository = repository;
    }

    @Override
    public void save(PasswordCredential credential) {
        repository.save(PasswordCredentialJpaEntity.from(credential));
    }

    @Override
    public Optional<PasswordCredential> findByUserId(UserId userId) {
        return repository.findById(userId.value()).map(PasswordCredentialJpaEntity::toDomain);
    }
}
