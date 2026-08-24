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
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * A mecânica compartilhada por todas as contas de bootstrap de desenvolvimento.
 *
 * <p>Existe porque a quarta cópia seria a que quebra. Admin e conferente já eram o mesmo procedimento
 * escrito duas vezes; com o operador e a vizinha seriam quatro lugares para lembrar de mudar quando a
 * criação de conta mudar — e o esquecido só apareceria como um ambiente local que sobe torto.
 *
 * <p><strong>A separação em duas transações não é estilo.</strong> A conta precisa estar
 * <em>commitada</em> antes do INSERT da associação, que a referencia por FK.
 *
 * <p>Tudo aqui é de desenvolvimento e cada chamador tem o próprio interruptor de configuração. Nenhuma
 * destas contas existe fora do perfil {@code local}.
 */
@Component
class BootstrapAccountSeeder {
    private static final Logger log = LoggerFactory.getLogger(BootstrapAccountSeeder.class);

    private final SecurityUserRepository users;
    private final PasswordCredentialRepository credentials;
    private final GroupMembershipRepository memberships;
    private final PasswordHasher passwordHasher;
    private final AuditTrail audit;
    private final TransactionTemplate transaction;

    BootstrapAccountSeeder(
            SecurityUserRepository users,
            PasswordCredentialRepository credentials,
            GroupMembershipRepository memberships,
            PasswordHasher passwordHasher,
            AuditTrail audit,
            PlatformTransactionManager transactionManager) {
        this.users = users;
        this.credentials = credentials;
        this.memberships = memberships;
        this.passwordHasher = passwordHasher;
        this.audit = audit;
        this.transaction = new TransactionTemplate(transactionManager);
    }

    /**
     * Garante conta e associação, de forma idempotente.
     *
     * @param breweryId cervejaria da associação. <strong>{@code null} é associação global</strong>, e dá
     *     acesso a <em>todas</em> as cervejarias — ver {@code SessionContextResolver}. Uma conta que
     *     precisa enxergar uma casa só recebe o id dela aqui, e não {@code null}.
     */
    void seed(String label, String email, String password, String name, String groupCode, UUID breweryId) {
        var address = new EmailAddress(email);
        var userId = transaction.execute(
                status -> ensureAccount(label, address, new RawPassword(password), name));
        transaction.executeWithoutResult(
                status -> ensureMembership(label, userId, address, groupCode, breweryId));
    }

    private UserId ensureAccount(String label, EmailAddress email, RawPassword password, String name) {
        var existing = users.findByNormalizedEmail(email.normalized()).orElse(null);
        if (existing != null) {
            return existing.id();
        }
        var user = SecurityUser.activeAccount(email, new DisplayName(name), Instant.now());
        users.save(user);
        credentials.save(
                new PasswordCredential(user.id(), passwordHasher.hash(password.value()), "delegating"));
        log.info("bootstrap-{}: conta criada para {}", label, email.normalized());
        return user.id();
    }

    private void ensureMembership(
            String label, UserId userId, EmailAddress email, String groupCode, UUID breweryId) {
        var groupId = memberships.groupIdByCode(groupCode)
                .orElseThrow(() -> new IllegalStateException("grupo " + groupCode + " não semeado"));
        if (memberships.hasActiveMembership(userId, groupId, breweryId)) {
            return;
        }
        memberships.addMembership(userId, groupId, breweryId);
        audit.record(AuditEvent.success(breweryId, userId.value(), "security.bootstrap." + label,
                "security_user", userId.value().toString(), Map.of("group", groupCode)));
        log.info("bootstrap-{}: {} associado ao grupo {}", label, email.normalized(), groupCode);
    }
}
