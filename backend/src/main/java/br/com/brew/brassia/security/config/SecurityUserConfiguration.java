package br.com.brew.brassia.security.config;

import br.com.brew.brassia.audit.AuditTrail;
import br.com.brew.brassia.security.application.port.inbound.AcceptInvitationUseCase;
import br.com.brew.brassia.security.application.port.inbound.AdministerAccountUseCase;
import br.com.brew.brassia.security.application.port.inbound.InviteUserUseCase;
import br.com.brew.brassia.security.application.port.inbound.ListUsersUseCase;
import br.com.brew.brassia.security.application.port.outbound.AccountTokenRepository;
import br.com.brew.brassia.security.application.port.outbound.NotificationGateway;
import br.com.brew.brassia.security.application.port.outbound.SecurityUserRepository;
import br.com.brew.brassia.security.application.port.outbound.TokenHasher;
import br.com.brew.brassia.security.application.port.outbound.UserSessionRegistry;
import br.com.brew.brassia.security.application.service.AcceptInvitationHandler;
import br.com.brew.brassia.security.application.service.AdministerAccountHandler;
import br.com.brew.brassia.security.application.service.InviteUserHandler;
import br.com.brew.brassia.security.application.service.ListUsersHandler;
import java.util.Objects;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

@Configuration(proxyBeanMethods = false)
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
            TokenHasher tokenHasher,
            AuditTrail audit,
            PlatformTransactionManager transactionManager) {
        var handler = new AcceptInvitationHandler(users, tokens, tokenHasher, audit);
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
}
