package br.com.brew.brassia.security.adapter.outbound.persistence;

import br.com.brew.brassia.security.application.port.outbound.AccountTokenRepository;
import br.com.brew.brassia.security.domain.AccountToken;
import java.util.Optional;
import org.springframework.stereotype.Repository;

// Não pode ser final: @Repository é proxiado (CGLIB) para tradução de exceções.
@Repository
class JpaAccountTokenRepositoryAdapter implements AccountTokenRepository {
    private final SpringDataAccountTokenJpaRepository repository;

    JpaAccountTokenRepositoryAdapter(SpringDataAccountTokenJpaRepository repository) {
        this.repository = repository;
    }

    @Override
    public void save(AccountToken token) {
        repository.save(AccountTokenJpaEntity.from(token));
    }

    @Override
    public Optional<AccountToken> findInvitationByHash(String tokenHash) {
        return findByHashAndType(tokenHash, AccountToken.Type.INVITATION);
    }

    @Override
    public Optional<AccountToken> findByHashAndType(String tokenHash, AccountToken.Type type) {
        return repository.findByTokenHashAndTokenType(tokenHash, type.name())
                .map(AccountTokenJpaEntity::toDomain);
    }
}
