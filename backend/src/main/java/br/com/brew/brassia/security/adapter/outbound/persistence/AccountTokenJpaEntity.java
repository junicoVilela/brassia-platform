package br.com.brew.brassia.security.adapter.outbound.persistence;

import br.com.brew.brassia.security.domain.AccountToken;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "account_token")
class AccountTokenJpaEntity {
    @Id private UUID id;
    private UUID userId;
    private String tokenType;
    private String tokenHash;
    private Instant expiresAt;
    private Instant usedAt;

    protected AccountTokenJpaEntity() {}

    static AccountTokenJpaEntity from(AccountToken token) {
        var entity = new AccountTokenJpaEntity();
        entity.id = token.id();
        entity.userId = token.userId().value();
        entity.tokenType = token.type().name();
        entity.tokenHash = token.tokenHash();
        entity.expiresAt = token.expiresAt();
        entity.usedAt = token.usedAt();
        return entity;
    }
}
