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
 * Garante, de forma idempotente, uma <strong>segunda</strong> pessoa em desenvolvimento.
 *
 * <p><strong>Ela não é um administrador a mais: é a outra pessoa.</strong> Os fluxos com separação de
 * deveres — a carga planejada por um e liberada por outro (LOG-001) — não têm caminho feliz com um
 * usuário só, e é justamente o caminho depois da recusa que ninguém conseguia exercitar de ponta a ponta.
 *
 * <p>Ela entra no mesmo grupo {@code ADMINISTRATORS} do admin de bootstrap, e isso é deliberado: a regra
 * que se quer exercitar é a de <strong>pessoas diferentes</strong>, e não a de permissões diferentes. O
 * agregado recusa a mesma pessoa nos dois papéis mesmo quando ela tem as duas alçadas — e é essa recusa,
 * a que sobrevive a quem tem tudo, que precisa de prova.
 *
 * <p>Roda com a mesma mecânica do {@link BootstrapAdminInitializer}, inclusive a separação em duas
 * transações: a conta precisa estar commitada antes do INSERT da associação, que a referencia por FK.
 */
@Component
class BootstrapCheckerInitializer implements ApplicationRunner {
    private static final Logger log = LoggerFactory.getLogger(BootstrapCheckerInitializer.class);

    private final BootstrapCheckerProperties properties;
    private final SecurityUserRepository users;
    private final PasswordCredentialRepository credentials;
    private final GroupMembershipRepository memberships;
    private final PasswordHasher passwordHasher;
    private final AuditTrail audit;
    private final TransactionTemplate transaction;

    BootstrapCheckerInitializer(
            BootstrapCheckerProperties properties,
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
            log.warn("bootstrap-checker habilitado sem email/senha; ignorando.");
            return;
        }

        var email = new EmailAddress(properties.email());
        var password = new RawPassword(properties.password());

        var userId = transaction.execute(status -> ensureAccount(email, password));
        transaction.executeWithoutResult(status -> ensureMembership(userId, email));
    }

    private UserId ensureAccount(EmailAddress email, RawPassword password) {
        var existing = users.findByNormalizedEmail(email.normalized()).orElse(null);
        if (existing != null) {
            return existing.id();
        }
        var user = SecurityUser.activeAccount(email, new DisplayName(properties.name()), Instant.now());
        users.save(user);
        credentials.save(
                new PasswordCredential(user.id(), passwordHasher.hash(password.value()), "delegating"));
        log.info("bootstrap-checker: segunda conta criada para {}", email.normalized());
        return user.id();
    }

    private void ensureMembership(UserId userId, EmailAddress email) {
        var groupId = memberships.groupIdByCode(BootstrapAdminInitializer.ADMIN_GROUP)
                .orElseThrow(() -> new IllegalStateException(
                        "grupo " + BootstrapAdminInitializer.ADMIN_GROUP + " não semeado"));
        if (memberships.hasActiveMembership(userId, groupId, null)) {
            return;
        }
        memberships.addMembership(userId, groupId, null);
        audit.record(AuditEvent.success(null, userId.value(), "security.bootstrap.checker",
                "security_user", userId.value().toString(),
                Map.of("group", BootstrapAdminInitializer.ADMIN_GROUP)));
        log.info("bootstrap-checker: {} associado ao grupo {}", email.normalized(),
                BootstrapAdminInitializer.ADMIN_GROUP);
    }
}
