package br.com.brew.brassia.security.domain;

import static org.assertj.core.api.Assertions.assertThat;

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
        assertThat(user.version()).isZero();
    }
}
