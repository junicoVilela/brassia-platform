package br.com.brew.brassia.security.adapter.outbound.persistence;

import br.com.brew.brassia.security.domain.AccountStatus;
import br.com.brew.brassia.security.domain.DisplayName;
import br.com.brew.brassia.security.domain.EmailAddress;
import br.com.brew.brassia.security.domain.SecurityUser;
import br.com.brew.brassia.security.domain.UserId;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "security_user")
class SecurityUserJpaEntity {
    @Id private UUID id;
    private String email;
    private String normalizedEmail;
    private String displayName;
    private String status;
    private Instant emailVerifiedAt;
    @Version private long version;

    protected SecurityUserJpaEntity() {}

    static SecurityUserJpaEntity from(SecurityUser user) {
        var entity = new SecurityUserJpaEntity();
        entity.id = user.id().value();
        entity.email = user.email().value();
        entity.normalizedEmail = user.email().normalized();
        entity.displayName = user.displayName().value();
        entity.status = user.status().name();
        entity.emailVerifiedAt = user.emailVerifiedAt();
        entity.version = user.version();
        return entity;
    }

    SecurityUser toDomain() {
        return SecurityUser.reconstitute(
                new UserId(id),
                new EmailAddress(email),
                new DisplayName(displayName),
                AccountStatus.valueOf(status),
                emailVerifiedAt,
                version);
    }
}
