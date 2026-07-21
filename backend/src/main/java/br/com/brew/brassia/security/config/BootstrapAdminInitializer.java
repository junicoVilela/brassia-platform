package br.com.brew.brassia.security.config;

import br.com.brew.brassia.audit.AuditEvent;
import br.com.brew.brassia.audit.AuditTrail;
import br.com.brew.brassia.security.application.port.outbound.GroupMembershipRepository;
import br.com.brew.brassia.security.application.port.outbound.PasswordCredentialRepository;
import br.com.brew.brassia.security.application.port.outbound.PasswordHasher;
import br.com.brew.brassia.security.application.port.outbound.SecurityUserRepository;
import br.com.brew.brassia.security.domain.DisplayName;
import br.com.brew.brassia.security.domain.EmailAddress;
import br.com.brew.brassia.security.domain.PasswordCredential;
import br.com.brew.brassia.security.domain.RawPassword;
import br.com.brew.brassia.security.domain.SecurityUser;
import br.com.brew.brassia.security.domain.UserId;
import java.time.Instant;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Garante, de forma idempotente, um administrador de bootstrap ao subir a app
 * (quando habilitado por config). Cria a conta ACTIVE + credencial se ausente e
 * assegura a associação ao grupo de sistema {@code ADMINISTRATORS}.
 *
 * <p>A conta (via JPA) e a associação (via JDBC) rodam em transações separadas:
 * a conta precisa estar <em>commitada</em> antes do INSERT da associação, que a
 * referencia por FK.
 */
@Component
class BootstrapAdminInitializer implements ApplicationRunner {
    static final String ADMIN_GROUP = "ADMINISTRATORS";
    private static final Logger log = LoggerFactory.getLogger(BootstrapAdminInitializer.class);

    private final BootstrapAdminProperties properties;
    private final SecurityUserRepository users;
    private final PasswordCredentialRepository credentials;
    private final GroupMembershipRepository memberships;
    private final PasswordHasher passwordHasher;
    private final AuditTrail audit;
    private final TransactionTemplate transaction;

    BootstrapAdminInitializer(
            BootstrapAdminProperties properties,
            SecurityUserRepository users,
            PasswordCredentialRepository credentials,
            GroupMembershipRepository memberships,
            PasswordHasher passwordHasher,
            AuditTrail audit,
            PlatformTransactionManager transactionManager) {
        this.properties = properties;
        this.users = users;
        this.credentials = credentials;
        this.memberships = memberships;
        this.passwordHasher = passwordHasher;
        this.audit = audit;
        this.transaction = new TransactionTemplate(transactionManager);
    }

    @Override
    public void run(ApplicationArguments args) {
        if (!properties.enabled()) {
            return;
        }
        if (properties.email() == null || properties.email().isBlank()
                || properties.password() == null || properties.password().isBlank()) {
            log.warn("bootstrap-admin habilitado sem email/senha; ignorando.");
            return;
        }

        var email = new EmailAddress(properties.email());
        var password = new RawPassword(properties.password());

        var userId = transaction.execute(status -> ensureAccount(email, password));
        transaction.executeWithoutResult(status -> ensureAdminMembership(userId, email));
    }

    private UserId ensureAccount(EmailAddress email, RawPassword password) {
        var existing = users.findByNormalizedEmail(email.normalized()).orElse(null);
        if (existing != null) {
            return existing.id();
        }
        var user = SecurityUser.activeAccount(email, new DisplayName(properties.name()), Instant.now());
        users.save(user);
        credentials.save(new PasswordCredential(user.id(), passwordHasher.hash(password.value()), "delegating"));
        log.info("bootstrap-admin: conta administrativa criada para {}", email.normalized());
        return user.id();
    }

    private void ensureAdminMembership(UserId userId, EmailAddress email) {
        var groupId = memberships.groupIdByCode(ADMIN_GROUP)
                .orElseThrow(() -> new IllegalStateException("grupo " + ADMIN_GROUP + " não semeado"));
        if (memberships.hasActiveMembership(userId, groupId, null)) {
            return;
        }
        memberships.addMembership(userId, groupId, null);
        audit.record(AuditEvent.success(null, userId.value(), "security.bootstrap.admin",
                "security_user", userId.value().toString(), Map.of("group", ADMIN_GROUP)));
        log.info("bootstrap-admin: {} associado ao grupo {}", email.normalized(), ADMIN_GROUP);
    }
}
