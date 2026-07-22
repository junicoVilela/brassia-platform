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
    br.com.brew.brassia.security.application.service.PasswordPolicy passwordPolicy(
            br.com.brew.brassia.security.application.port.outbound.CompromisedPasswordChecker checker) {
        return new br.com.brew.brassia.security.application.service.PasswordPolicy(checker);
    }

    @Bean
    AcceptInvitationUseCase acceptInvitationUseCase(
            SecurityUserRepository users,
            AccountTokenRepository tokens,
            PasswordCredentialRepository credentials,
            TokenHasher tokenHasher,
            PasswordHasher passwordHasher,
            br.com.brew.brassia.security.application.service.PasswordPolicy passwordPolicy,
            AuditTrail audit,
            PlatformTransactionManager transactionManager) {
        var handler = new AcceptInvitationHandler(users, tokens, credentials, tokenHasher, passwordHasher, passwordPolicy, audit);
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
            br.com.brew.brassia.security.application.port.inbound.HasActiveMfaQuery mfaQuery,
            AuditTrail audit,
            PlatformTransactionManager transactionManager) {
        var handler = new AuthenticateUserHandler(users, credentials, passwordHasher, mfaQuery, audit);
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
            br.com.brew.brassia.security.application.service.SegregationChecker segregation,
            AuditTrail audit,
            PlatformTransactionManager transactionManager) {
        var handler = new br.com.brew.brassia.security.application.service.ManageMembershipHandler(users, memberships, segregation, audit);
        var transaction = new TransactionTemplate(transactionManager);
        return new br.com.brew.brassia.security.application.port.inbound.ManageMembershipUseCase() {
            @Override public void grant(Command c) { transaction.executeWithoutResult(s -> handler.grant(c)); }
            @Override public void revoke(Command c) { transaction.executeWithoutResult(s -> handler.revoke(c)); }
        };
    }

    @Bean
    br.com.brew.brassia.security.application.port.inbound.ChangePasswordUseCase changePasswordUseCase(
            PasswordCredentialRepository credentials,
            br.com.brew.brassia.security.application.port.outbound.PasswordHistoryRepository history,
            PasswordHasher passwordHasher,
            br.com.brew.brassia.security.application.service.PasswordPolicy passwordPolicy,
            AuditTrail audit,
            @org.springframework.beans.factory.annotation.Value("${brassia.security.password.history-size:5}") int historySize,
            PlatformTransactionManager transactionManager) {
        var handler = new br.com.brew.brassia.security.application.service.ChangePasswordHandler(
                credentials, history, passwordHasher, passwordPolicy, audit, historySize);
        var transaction = new TransactionTemplate(transactionManager);
        return command -> transaction.executeWithoutResult(status -> handler.handle(command));
    }

    @Bean
    br.com.brew.brassia.security.application.port.inbound.TemporaryAccessUseCase temporaryAccessUseCase(
            br.com.brew.brassia.security.application.port.outbound.TemporaryAccessRepository grants,
            SecurityUserRepository users,
            AuditTrail audit,
            PlatformTransactionManager transactionManager) {
        var handler = new br.com.brew.brassia.security.application.service.TemporaryAccessHandler(grants, users, audit);
        var transaction = new TransactionTemplate(transactionManager);
        return new br.com.brew.brassia.security.application.port.inbound.TemporaryAccessUseCase() {
            @Override public java.util.UUID request(RequestCommand c) {
                return Objects.requireNonNull(transaction.execute(s -> handler.request(c)));
            }
            @Override public void approve(java.util.UUID id, java.util.UUID actorId, java.util.UUID breweryId) {
                transaction.executeWithoutResult(s -> handler.approve(id, actorId, breweryId));
            }
            @Override public void revoke(java.util.UUID id, java.util.UUID actorId, java.util.UUID breweryId) {
                transaction.executeWithoutResult(s -> handler.revoke(id, actorId, breweryId));
            }
        };
    }

    @Bean
    br.com.brew.brassia.security.application.port.inbound.TemporaryAccessQuery temporaryAccessQuery(
            br.com.brew.brassia.security.application.port.outbound.TemporaryAccessRepository grants,
            SecurityUserRepository users,
            AuditTrail audit) {
        return new br.com.brew.brassia.security.application.service.TemporaryAccessHandler(grants, users, audit);
    }

    @Bean
    br.com.brew.brassia.security.application.port.inbound.ManageGroupUseCase manageGroupUseCase(
            br.com.brew.brassia.security.application.port.outbound.SecurityGroupRepository groups,
            AuditTrail audit,
            PlatformTransactionManager transactionManager) {
        var handler = new br.com.brew.brassia.security.application.service.ManageGroupHandler(groups, audit);
        var transaction = new TransactionTemplate(transactionManager);
        return new br.com.brew.brassia.security.application.port.inbound.ManageGroupUseCase() {
            @Override public Result create(CreateCommand c) {
                return Objects.requireNonNull(transaction.execute(s -> handler.create(c)));
            }
            @Override public Result update(UpdateCommand c) {
                return Objects.requireNonNull(transaction.execute(s -> handler.update(c)));
            }
        };
    }

    @Bean
    br.com.brew.brassia.security.application.port.inbound.ResolveSessionContextUseCase resolveSessionContextUseCase(
            BreweryAccessRepository breweryAccess,
            br.com.brew.brassia.brewery.BreweryDirectory breweryDirectory,
            EffectivePermissionsRepository permissions) {
        return new SessionContextResolver(breweryAccess, breweryDirectory, permissions);
    }

    @Bean
    br.com.brew.brassia.security.application.port.inbound.RecordLoginAttemptUseCase recordLoginAttemptUseCase(
            br.com.brew.brassia.security.application.port.outbound.LoginEventRepository loginEvents) {
        return new br.com.brew.brassia.security.application.service.RecordLoginAttemptHandler(loginEvents);
    }

    @Bean
    br.com.brew.brassia.security.application.port.inbound.LoginHistoryQuery loginHistoryQuery(
            br.com.brew.brassia.security.application.port.outbound.LoginEventRepository loginEvents) {
        return new br.com.brew.brassia.security.application.service.LoginHistoryQueryHandler(loginEvents);
    }

    @Bean
    br.com.brew.brassia.security.application.port.inbound.ManageOwnSessionsUseCase manageOwnSessionsUseCase(
            br.com.brew.brassia.security.application.port.outbound.UserSessionCatalog sessions) {
        return new br.com.brew.brassia.security.application.service.ManageOwnSessionsHandler(sessions);
    }
}
