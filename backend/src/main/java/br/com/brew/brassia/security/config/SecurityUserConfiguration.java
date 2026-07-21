package br.com.brew.brassia.security.config;

import br.com.brew.brassia.audit.AuditTrail;
import br.com.brew.brassia.security.application.port.inbound.AcceptInvitationUseCase;
import br.com.brew.brassia.security.application.port.inbound.AdministerAccountUseCase;
import br.com.brew.brassia.security.application.port.inbound.AuthenticateUserUseCase;
import br.com.brew.brassia.security.application.port.inbound.InviteUserUseCase;
import br.com.brew.brassia.security.application.port.inbound.ListUsersUseCase;
import br.com.brew.brassia.security.application.port.outbound.AccountTokenRepository;
import br.com.brew.brassia.security.application.port.outbound.BreweryAccessRepository;
import br.com.brew.brassia.security.application.port.outbound.EffectivePermissionsRepository;
import br.com.brew.brassia.security.application.port.outbound.NotificationGateway;
import br.com.brew.brassia.security.application.port.outbound.PasswordCredentialRepository;
import br.com.brew.brassia.security.application.port.outbound.PasswordHasher;
import br.com.brew.brassia.security.application.port.outbound.SecurityUserRepository;
import br.com.brew.brassia.security.application.port.outbound.TokenHasher;
import br.com.brew.brassia.security.application.port.outbound.UserSessionRegistry;
import br.com.brew.brassia.security.application.service.AcceptInvitationHandler;
import br.com.brew.brassia.security.application.service.AdministerAccountHandler;
import br.com.brew.brassia.security.application.service.AuthenticateUserHandler;
import br.com.brew.brassia.security.application.service.InviteUserHandler;
import br.com.brew.brassia.security.application.service.ListUsersHandler;
import br.com.brew.brassia.security.application.service.SessionContextResolver;
import java.util.Objects;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(BootstrapAdminProperties.class)
class SecurityUserConfiguration {
    @Bean
    InviteUserUseCase inviteUserUseCase(
            SecurityUserRepository users,
            AccountTokenRepository tokens,
            TokenHasher tokenHasher,
            NotificationGateway notifications,
            AuditTrail audit,
            PlatformTransactionManager transactionManager) {
        var handler = new InviteUserHandler(users, tokens, tokenHasher, notifications, audit);
        var transaction = new TransactionTemplate(transactionManager);
        return command -> Objects.requireNonNull(
                transaction.execute(status -> handler.handle(command)));
    }

    @Bean
    AcceptInvitationUseCase acceptInvitationUseCase(
            SecurityUserRepository users,
            AccountTokenRepository tokens,
            PasswordCredentialRepository credentials,
            TokenHasher tokenHasher,
            PasswordHasher passwordHasher,
            AuditTrail audit,
            PlatformTransactionManager transactionManager) {
        var handler = new AcceptInvitationHandler(users, tokens, credentials, tokenHasher, passwordHasher, audit);
        var transaction = new TransactionTemplate(transactionManager);
        return command -> Objects.requireNonNull(
                transaction.execute(status -> handler.handle(command)));
    }

    @Bean
    AdministerAccountUseCase administerAccountUseCase(
            SecurityUserRepository users,
            UserSessionRegistry sessions,
            AuditTrail audit,
            PlatformTransactionManager transactionManager) {
        var handler = new AdministerAccountHandler(users, sessions, audit);
        var transaction = new TransactionTemplate(transactionManager);
        return command -> Objects.requireNonNull(
                transaction.execute(status -> handler.handle(command)));
    }

    @Bean
    ListUsersUseCase listUsersUseCase(SecurityUserRepository users) {
        return new ListUsersHandler(users);
    }

    @Bean
    AuthenticateUserUseCase authenticateUserUseCase(
            SecurityUserRepository users,
            PasswordCredentialRepository credentials,
            PasswordHasher passwordHasher,
            AuditTrail audit,
            PlatformTransactionManager transactionManager) {
        var handler = new AuthenticateUserHandler(users, credentials, passwordHasher, audit);
        var transaction = new TransactionTemplate(transactionManager);
        return command -> Objects.requireNonNull(
                transaction.execute(status -> handler.handle(command)));
    }

    @Bean
    br.com.brew.brassia.security.application.port.inbound.AccessCatalogQuery accessCatalogQuery(
            br.com.brew.brassia.security.application.port.outbound.SecurityCatalogRepository catalog) {
        return new br.com.brew.brassia.security.application.service.AccessCatalogQueryHandler(catalog);
    }

    @Bean
    br.com.brew.brassia.security.application.port.inbound.ManageMembershipUseCase manageMembershipUseCase(
            SecurityUserRepository users,
            br.com.brew.brassia.security.application.port.outbound.GroupMembershipRepository memberships,
            AuditTrail audit,
            PlatformTransactionManager transactionManager) {
        var handler = new br.com.brew.brassia.security.application.service.ManageMembershipHandler(users, memberships, audit);
        var transaction = new TransactionTemplate(transactionManager);
        return new br.com.brew.brassia.security.application.port.inbound.ManageMembershipUseCase() {
            @Override public void grant(Command c) { transaction.executeWithoutResult(s -> handler.grant(c)); }
            @Override public void revoke(Command c) { transaction.executeWithoutResult(s -> handler.revoke(c)); }
        };
    }

    @Bean
    SessionContextResolver sessionContextResolver(
            BreweryAccessRepository breweryAccess,
            br.com.brew.brassia.brewery.BreweryDirectory breweryDirectory,
            EffectivePermissionsRepository permissions) {
        return new SessionContextResolver(breweryAccess, breweryDirectory, permissions);
    }
}
