package br.com.brew.brassia.security.domain;

import java.time.Instant;
import java.util.Objects;

/**
 * Identidade interna (agregado). O e-mail normalizado é a autoridade de
 * unicidade global; o vínculo com cervejaria é feito por escopos/grupos
 * (SEC-004/005), não por este agregado.
 */
public final class SecurityUser {
    private final UserId id;
    private final EmailAddress email;
    private DisplayName displayName;
    private AccountStatus status;
    private Instant emailVerifiedAt;
    private long version;

    private SecurityUser(UserId id, EmailAddress email, DisplayName displayName,
            AccountStatus status, Instant emailVerifiedAt, long version) {
        this.id = Objects.requireNonNull(id);
        this.email = Objects.requireNonNull(email);
        this.displayName = Objects.requireNonNull(displayName);
        this.status = Objects.requireNonNull(status);
        this.emailVerifiedAt = emailVerifiedAt;
        this.version = version;
    }

    /** Cria uma conta recém-convidada, em estado {@link AccountStatus#INVITED}. */
    public static SecurityUser invite(EmailAddress email, DisplayName displayName) {
        return new SecurityUser(UserId.newId(), email, displayName, AccountStatus.INVITED, null, 0);
    }

    /**
     * Cria uma conta já ativa e com e-mail verificado. Uso restrito ao bootstrap
     * administrativo (não passa pelo fluxo de convite/aceite).
     */
    public static SecurityUser activeAccount(EmailAddress email, DisplayName displayName, Instant now) {
        return new SecurityUser(UserId.newId(), email, displayName, AccountStatus.ACTIVE,
                Objects.requireNonNull(now, "now"), 0);
    }

    /** Reconstrói o agregado a partir da persistência (sem regra de criação). */
    public static SecurityUser reconstitute(UserId id, EmailAddress email, DisplayName displayName,
            AccountStatus status, Instant emailVerifiedAt, long version) {
        return new SecurityUser(id, email, displayName, status, emailVerifiedAt, version);
    }

    /**
     * Aceite de convite: verifica o e-mail e ativa a conta. Só uma conta
     * {@link AccountStatus#INVITED} pode ser ativada por esta via.
     */
    public void activateFromInvitation(Instant now) {
        if (status != AccountStatus.INVITED) {
            throw new IllegalStateException("conta não está em estado de convite");
        }
        this.emailVerifiedAt = Objects.requireNonNull(now, "now");
        this.status = AccountStatus.ACTIVE;
    }

    /** Bloqueia uma conta ativa (ex.: por decisão administrativa). */
    public void block() {
        if (status != AccountStatus.ACTIVE) {
            throw new IllegalStateException("somente conta ativa pode ser bloqueada");
        }
        this.status = AccountStatus.LOCKED;
    }

    /** Desbloqueia uma conta bloqueada, retornando-a ao estado ativo. */
    public void unblock() {
        if (status != AccountStatus.LOCKED) {
            throw new IllegalStateException("conta não está bloqueada");
        }
        this.status = AccountStatus.ACTIVE;
    }

    /** Desativa a conta (terminal). Uma conta já desativada não é reprocessada. */
    public void disable() {
        if (status == AccountStatus.DISABLED) {
            throw new IllegalStateException("conta já desativada");
        }
        this.status = AccountStatus.DISABLED;
    }

    public UserId id() { return id; }
    public EmailAddress email() { return email; }
    public DisplayName displayName() { return displayName; }
    public AccountStatus status() { return status; }
    public Instant emailVerifiedAt() { return emailVerifiedAt; }
    public long version() { return version; }
}
