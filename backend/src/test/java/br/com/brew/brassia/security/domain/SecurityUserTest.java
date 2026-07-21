package br.com.brew.brassia.security.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;
import org.junit.jupiter.api.Test;

class SecurityUserTest {

    @Test
    void invitedUserStartsInInvitedStatus() {
        var user = SecurityUser.invite(new EmailAddress("brewer@example.com"), new DisplayName("Brewer"));

        assertThat(user.id()).isNotNull();
        assertThat(user.id().value()).isNotNull();
        assertThat(user.status()).isEqualTo(AccountStatus.INVITED);
        assertThat(user.email().normalized()).isEqualTo("brewer@example.com");
        assertThat(user.displayName().value()).isEqualTo("Brewer");
        assertThat(user.emailVerifiedAt()).isNull();
        assertThat(user.version()).isZero();
    }

    @Test
    void activateFromInvitationVerifiesEmailAndActivates() {
        var user = SecurityUser.invite(new EmailAddress("brewer@example.com"), new DisplayName("Brewer"));
        var now = Instant.now();

        user.activateFromInvitation(now);

        assertThat(user.status()).isEqualTo(AccountStatus.ACTIVE);
        assertThat(user.emailVerifiedAt()).isEqualTo(now);
    }

    @Test
    void activateFromInvitationRejectsNonInvitedAccount() {
        var user = SecurityUser.reconstitute(UserId.newId(), new EmailAddress("brewer@example.com"),
                new DisplayName("Brewer"), AccountStatus.ACTIVE, Instant.now(), 3);

        assertThatThrownBy(() -> user.activateFromInvitation(Instant.now()))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void reconstitutePreservesState() {
        var id = UserId.newId();
        var verifiedAt = Instant.now();
        var user = SecurityUser.reconstitute(id, new EmailAddress("brewer@example.com"),
                new DisplayName("Brewer"), AccountStatus.LOCKED, verifiedAt, 7);

        assertThat(user.id()).isEqualTo(id);
        assertThat(user.status()).isEqualTo(AccountStatus.LOCKED);
        assertThat(user.emailVerifiedAt()).isEqualTo(verifiedAt);
        assertThat(user.version()).isEqualTo(7);
    }
}
