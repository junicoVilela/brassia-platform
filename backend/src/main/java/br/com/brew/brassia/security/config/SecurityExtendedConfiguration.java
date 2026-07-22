package br.com.brew.brassia.security.config;

import br.com.brew.brassia.audit.AuditTrail;
import br.com.brew.brassia.security.application.port.inbound.AuthenticateApiKeyUseCase;
import br.com.brew.brassia.security.application.port.inbound.AuthenticateUserUseCase;
import br.com.brew.brassia.security.application.port.inbound.CompleteMfaLoginUseCase;
import br.com.brew.brassia.security.application.port.inbound.ConfirmEmailVerificationUseCase;
import br.com.brew.brassia.security.application.port.inbound.ConfirmTotpUseCase;
import br.com.brew.brassia.security.application.port.inbound.DisableTotpUseCase;
import br.com.brew.brassia.security.application.port.inbound.EnrollTotpUseCase;
import br.com.brew.brassia.security.application.port.inbound.HasActiveMfaQuery;
import br.com.brew.brassia.security.application.port.inbound.ManageAccessReviewUseCase;
import br.com.brew.brassia.security.application.port.inbound.ManageFederationProviderUseCase;
import br.com.brew.brassia.security.application.port.inbound.ManageSecurityAlertUseCase;
import br.com.brew.brassia.security.application.port.inbound.ManageSegregationUseCase;
import br.com.brew.brassia.security.application.port.inbound.ManageServiceAccountUseCase;
import br.com.brew.brassia.security.application.port.inbound.PerformLoginUseCase;
import br.com.brew.brassia.security.application.port.inbound.RecordLoginAttemptUseCase;
import br.com.brew.brassia.security.application.port.inbound.RegenerateRecoveryCodesUseCase;
import br.com.brew.brassia.security.application.port.inbound.RequestEmailVerificationUseCase;
import br.com.brew.brassia.security.application.port.inbound.RequestPasswordResetUseCase;
import br.com.brew.brassia.security.application.port.inbound.ResetPasswordUseCase;
import br.com.brew.brassia.security.application.port.inbound.ScimProvisioningUseCase;
import br.com.brew.brassia.security.application.port.outbound.AccessReviewRepository;
import br.com.brew.brassia.security.application.port.outbound.AccountTokenRepository;
import br.com.brew.brassia.security.application.port.outbound.ApiCredentialRepository;
import br.com.brew.brassia.security.application.port.outbound.EffectivePermissionsRepository;
import br.com.brew.brassia.security.application.port.outbound.ExternalIdentityRepository;
import br.com.brew.brassia.security.application.port.outbound.FederationProviderRepository;
import br.com.brew.brassia.security.application.port.outbound.GroupMembershipRepository;
import br.com.brew.brassia.security.application.port.outbound.GroupPermissionRepository;
import br.com.brew.brassia.security.application.port.outbound.LoginThrottleRepository;
import br.com.brew.brassia.security.application.port.outbound.MfaFactorRepository;
import br.com.brew.brassia.security.application.port.outbound.MfaSecretCipher;
import br.com.brew.brassia.security.application.port.outbound.NotificationGateway;
import br.com.brew.brassia.security.application.port.outbound.PasswordCredentialRepository;
import br.com.brew.brassia.security.application.port.outbound.PasswordHasher;
import br.com.brew.brassia.security.application.port.outbound.ProvisioningEventRepository;
import br.com.brew.brassia.security.application.port.outbound.RecoveryCodeRepository;
import br.com.brew.brassia.security.application.port.outbound.ScimGroupMappingRepository;
import br.com.brew.brassia.security.application.port.outbound.SegregationRuleRepository;
import br.com.brew.brassia.security.application.port.outbound.SecurityAlertRepository;
import br.com.brew.brassia.security.application.port.outbound.SecurityUserRepository;
import br.com.brew.brassia.security.application.port.outbound.ServiceAccountRepository;
import br.com.brew.brassia.security.application.port.outbound.TokenHasher;
import br.com.brew.brassia.security.application.port.outbound.UserSessionRegistry;
import br.com.brew.brassia.security.application.service.AccessReviewHandler;
import br.com.brew.brassia.security.application.service.AccountRecoveryHandler;
import br.com.brew.brassia.security.application.service.FederationProviderHandler;
import br.com.brew.brassia.security.application.service.LoginThrottleService;
import br.com.brew.brassia.security.application.service.MfaManagementHandler;
import br.com.brew.brassia.security.application.service.PerformLoginHandler;
import br.com.brew.brassia.security.application.service.OidcTokenClaimsValidator;
import br.com.brew.brassia.security.application.service.PasswordPolicy;
import br.com.brew.brassia.security.application.service.SamlAssertionValidator;
import br.com.brew.brassia.security.application.service.ScimProvisioningHandler;
import br.com.brew.brassia.security.application.service.SecurityAlertHandler;
import br.com.brew.brassia.security.application.service.SegregationChecker;
import br.com.brew.brassia.security.application.service.ServiceAccountHandler;
import java.util.Objects;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

@Configuration(proxyBeanMethods = false)
class SecurityExtendedConfiguration {

    @Bean
    MfaManagementHandler mfaManagementHandler(
            MfaFactorRepository factors, RecoveryCodeRepository recoveryCodes, MfaSecretCipher cipher,
            SecurityUserRepository users, PasswordCredentialRepository credentials, PasswordHasher passwordHasher,
            TokenHasher tokenHasher, AuditTrail audit) {
        return new MfaManagementHandler(factors, recoveryCodes, cipher, users, credentials, passwordHasher, tokenHasher, audit);
    }

    @Bean EnrollTotpUseCase enrollTotpUseCase(MfaManagementHandler h) { return h::enroll; }
    @Bean ConfirmTotpUseCase confirmTotpUseCase(MfaManagementHandler h) { return h::confirm; }
    @Bean DisableTotpUseCase disableTotpUseCase(MfaManagementHandler h) { return h::disable; }
    @Bean RegenerateRecoveryCodesUseCase regenerateRecoveryCodesUseCase(MfaManagementHandler h) { return h::regenerate; }
    @Bean HasActiveMfaQuery hasActiveMfaQuery(MfaManagementHandler h) { return h::hasActiveTotp; }
    @Bean CompleteMfaLoginUseCase completeMfaLoginUseCase(MfaManagementHandler h, PlatformTransactionManager tm) {
        var tx = new TransactionTemplate(tm);
        return command -> Objects.requireNonNull(tx.execute(s -> h.completeMfaLogin(command)));
    }

    @Bean AccountRecoveryHandler accountRecoveryHandler(
            SecurityUserRepository users, AccountTokenRepository tokens, TokenHasher tokenHasher,
            NotificationGateway notifications, PasswordCredentialRepository credentials, PasswordHasher passwordHasher,
            PasswordPolicy passwordPolicy, UserSessionRegistry sessions, AuditTrail audit) {
        return new AccountRecoveryHandler(users, tokens, tokenHasher, notifications, credentials,
                passwordHasher, passwordPolicy, sessions, audit);
    }

    @Bean RequestPasswordResetUseCase requestPasswordResetUseCase(AccountRecoveryHandler h, PlatformTransactionManager tm) {
        var tx = new TransactionTemplate(tm);
        return command -> tx.executeWithoutResult(s -> h.requestPasswordReset(command));
    }
    @Bean ResetPasswordUseCase resetPasswordUseCase(AccountRecoveryHandler h, PlatformTransactionManager tm) {
        var tx = new TransactionTemplate(tm);
        return command -> tx.executeWithoutResult(s -> h.resetPassword(command));
    }
    @Bean RequestEmailVerificationUseCase requestEmailVerificationUseCase(AccountRecoveryHandler h, PlatformTransactionManager tm) {
        var tx = new TransactionTemplate(tm);
        return command -> tx.executeWithoutResult(s -> h.requestEmailVerification(command));
    }
    @Bean ConfirmEmailVerificationUseCase confirmEmailVerificationUseCase(AccountRecoveryHandler h, PlatformTransactionManager tm) {
        var tx = new TransactionTemplate(tm);
        return command -> tx.executeWithoutResult(s -> h.confirmEmailVerification(command));
    }

    @Bean ServiceAccountHandler serviceAccountHandler(
            ServiceAccountRepository accounts, ApiCredentialRepository credentials,
            TokenHasher tokenHasher, AuditTrail audit) {
        return new ServiceAccountHandler(accounts, credentials, tokenHasher, audit);
    }

    @Bean ManageServiceAccountUseCase manageServiceAccountUseCase(ServiceAccountHandler h, PlatformTransactionManager tm) {
        var tx = new TransactionTemplate(tm);
        return new ManageServiceAccountUseCase() {
            @Override public ServiceAccountView create(CreateCommand c) {
                return Objects.requireNonNull(tx.execute(s -> h.create(c)));
            }
            @Override public java.util.List<ServiceAccountView> list(ListCommand c) {
                return Objects.requireNonNull(tx.execute(s -> h.list(c)));
            }
            @Override public IssueCredentialResult issueCredential(IssueCommand c) {
                return Objects.requireNonNull(tx.execute(s -> h.issueCredential(c)));
            }
            @Override public void revokeCredential(RevokeCommand c) {
                tx.executeWithoutResult(s -> h.revokeCredential(c));
            }
        };
    }
    @Bean AuthenticateApiKeyUseCase authenticateApiKeyUseCase(ServiceAccountHandler h) { return h::authenticate; }

    // Sem TransactionTemplate: cada colaborador (throttle, autenticação, histórico)
    // persiste seu efeito de forma independente, preservando o registro de falha
    // mesmo quando o login é rejeitado. Ver PerformLoginHandler.
    @Bean
    PerformLoginUseCase performLoginUseCase(LoginThrottleService throttle,
            AuthenticateUserUseCase authenticate, RecordLoginAttemptUseCase loginHistory) {
        return new PerformLoginHandler(throttle, authenticate, loginHistory);
    }

    @Bean LoginThrottleService loginThrottleService(
            LoginThrottleRepository throttle, SecurityAlertRepository alerts, TokenHasher tokenHasher) {
        return new LoginThrottleService(throttle, alerts, tokenHasher);
    }

    @Bean SegregationChecker segregationChecker(
            SegregationRuleRepository rules, EffectivePermissionsRepository effectivePermissions,
            GroupPermissionRepository groupPermissions) {
        return new SegregationChecker(rules, effectivePermissions, groupPermissions);
    }

    @Bean AccessReviewHandler accessReviewHandler(
            AccessReviewRepository reviews, GroupMembershipRepository memberships,
            SegregationRuleRepository segregationRules, AuditTrail audit) {
        return new AccessReviewHandler(reviews, memberships, segregationRules, audit);
    }

    @Bean ManageAccessReviewUseCase manageAccessReviewUseCase(AccessReviewHandler h, PlatformTransactionManager tm) {
        var tx = new TransactionTemplate(tm);
        return new ManageAccessReviewUseCase() {
            @Override public java.util.UUID createReview(CreateReviewCommand c) {
                return Objects.requireNonNull(tx.execute(s -> h.createReview(c)));
            }
            @Override public void decideItem(DecideItemCommand c) {
                tx.executeWithoutResult(s -> h.decideItem(c));
            }
            @Override public java.util.List<AccessReviewRepository.ReviewView> listReviews(java.util.UUID breweryId) {
                return h.listReviews(breweryId);
            }
            @Override public java.util.List<AccessReviewRepository.ItemView> listItems(java.util.UUID reviewId) {
                return h.listItems(reviewId);
            }
        };
    }
    @Bean ManageSegregationUseCase manageSegregationUseCase(AccessReviewHandler h, PlatformTransactionManager tm) {
        var tx = new TransactionTemplate(tm);
        return new ManageSegregationUseCase() {
            @Override public java.util.UUID createRule(CreateRuleCommand c) {
                return Objects.requireNonNull(tx.execute(s -> h.createRule(c)));
            }
            @Override public java.util.List<SegregationRuleRepository.RuleView> listRules(java.util.UUID breweryId) {
                return h.listRules(breweryId);
            }
        };
    }

    @Bean SamlAssertionValidator samlAssertionValidator() { return new SamlAssertionValidator(); }
    @Bean OidcTokenClaimsValidator oidcTokenClaimsValidator() { return new OidcTokenClaimsValidator(); }

    @Bean FederationProviderHandler federationProviderHandler(
            FederationProviderRepository providers, ExternalIdentityRepository identities,
            SamlAssertionValidator samlValidator, OidcTokenClaimsValidator oidcValidator, AuditTrail audit) {
        return new FederationProviderHandler(providers, identities, samlValidator, oidcValidator, audit);
    }

    @Bean ManageFederationProviderUseCase manageFederationProviderUseCase(FederationProviderHandler h, PlatformTransactionManager tm) {
        var tx = new TransactionTemplate(tm);
        return new ManageFederationProviderUseCase() {
            @Override public java.util.UUID create(CreateCommand c) {
                return Objects.requireNonNull(tx.execute(s -> h.create(c)));
            }
            @Override public java.util.List<FederationProviderRepository.ProviderView> list(java.util.UUID breweryId) {
                return h.list(breweryId);
            }
            @Override public void validate(ValidateCommand c) {
                tx.executeWithoutResult(s -> h.validate(c));
            }
            @Override public void linkIdentity(LinkCommand c) {
                tx.executeWithoutResult(s -> h.linkIdentity(c));
            }
            @Override public java.util.UUID resolveUserId(java.util.UUID providerId, String externalSubject) {
                return h.resolveUserId(providerId, externalSubject);
            }
        };
    }

    @Bean ScimProvisioningHandler scimProvisioningHandler(
            SecurityUserRepository users, ProvisioningEventRepository events,
            ScimGroupMappingRepository groupMappings,
            br.com.brew.brassia.security.application.port.inbound.AdministerAccountUseCase administerAccount) {
        return new ScimProvisioningHandler(users, events, groupMappings, administerAccount);
    }

    @Bean ScimProvisioningUseCase scimProvisioningUseCase(ScimProvisioningHandler h, PlatformTransactionManager tm) {
        var tx = new TransactionTemplate(tm);
        return new ScimProvisioningUseCase() {
            @Override public java.util.Map<String, Object> createUser(CreateUserCommand c) {
                return Objects.requireNonNull(tx.execute(s -> h.createUser(c)));
            }
            @Override public java.util.Map<String, Object> getUser(GetUserCommand c) { return h.getUser(c); }
            @Override public void patchUser(PatchUserCommand c) { tx.executeWithoutResult(s -> h.patchUser(c)); }
            @Override public void deleteUser(DeleteUserCommand c) { tx.executeWithoutResult(s -> h.deleteUser(c)); }
            @Override public java.util.Map<String, Object> createGroup(CreateGroupCommand c) {
                return Objects.requireNonNull(tx.execute(s -> h.createGroup(c)));
            }
            @Override public java.util.Map<String, Object> getGroup(GetGroupCommand c) { return h.getGroup(c); }
        };
    }

    @Bean SecurityAlertHandler securityAlertHandler(SecurityAlertRepository alerts, AuditTrail audit) {
        return new SecurityAlertHandler(alerts, audit);
    }

    @Bean ManageSecurityAlertUseCase manageSecurityAlertUseCase(SecurityAlertHandler h, PlatformTransactionManager tm) {
        var tx = new TransactionTemplate(tm);
        return new ManageSecurityAlertUseCase() {
            @Override public java.util.List<SecurityAlertRepository.AlertView> list(java.util.UUID breweryId, String status) {
                return h.list(breweryId, status);
            }
            @Override public void updateStatus(java.util.UUID breweryId, java.util.UUID actorId, java.util.UUID alertId, String status) {
                tx.executeWithoutResult(s -> h.updateStatus(breweryId, actorId, alertId, status));
            }
        };
    }
}
