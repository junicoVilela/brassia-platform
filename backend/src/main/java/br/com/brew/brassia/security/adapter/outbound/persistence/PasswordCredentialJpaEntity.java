package br.com.brew.brassia.security.adapter.outbound.persistence;

import br.com.brew.brassia.security.domain.PasswordCredential;
import br.com.brew.brassia.security.domain.UserId;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.util.UUID;

@Entity
@Table(name = "password_credential")
class PasswordCredentialJpaEntity {
    @Id private UUID userId;
    private String passwordHash;
    private String encoderId;

    protected PasswordCredentialJpaEntity() {}

    static PasswordCredentialJpaEntity from(PasswordCredential credential) {
        var entity = new PasswordCredentialJpaEntity();
        entity.userId = credential.userId().value();
        entity.passwordHash = credential.passwordHash();
        entity.encoderId = credential.encoderId();
        return entity;
    }

    PasswordCredential toDomain() {
        return new PasswordCredential(new UserId(userId), passwordHash, encoderId);
    }
}
