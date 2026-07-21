package br.com.brew.brassia.security.domain;

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
    private long version;

    private SecurityUser(UserId id, EmailAddress email, DisplayName displayName, AccountStatus status) {
        this.id = Objects.requireNonNull(id);
        this.email = Objects.requireNonNull(email);
        this.displayName = Objects.requireNonNull(displayName);
        this.status = Objects.requireNonNull(status);
    }

    /** Cria uma conta recém-convidada, em estado {@link AccountStatus#INVITED}. */
    public static SecurityUser invite(EmailAddress email, DisplayName displayName) {
        return new SecurityUser(UserId.newId(), email, displayName, AccountStatus.INVITED);
    }

    public UserId id() { return id; }
    public EmailAddress email() { return email; }
    public DisplayName displayName() { return displayName; }
    public AccountStatus status() { return status; }
    public long version() { return version; }
}
