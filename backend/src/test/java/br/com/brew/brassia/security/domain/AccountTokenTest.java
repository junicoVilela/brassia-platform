package br.com.brew.brassia.security.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Duration;
import java.time.Instant;
import org.junit.jupiter.api.Test;

class AccountTokenTest {

    private final UserId userId = UserId.newId();

    @Test
    void invitationIsNotExpiredBeforeExpiry() {
        var now = Instant.now();
        var token = AccountToken.invitation(userId, "hash", now.plus(Duration.ofHours(1)));

        assertThat(token.type()).isEqualTo(AccountToken.Type.INVITATION);
        assertThat(token.isExpired(now)).isFalse();
        assertThat(token.usedAt()).isNull();
    }

    @Test
    void isExpiredAtAndAfterExpiry() {
        var expiry = Instant.now();
        var token = AccountToken.invitation(userId, "hash", expiry);

        assertThat(token.isExpired(expiry)).isTrue();
        assertThat(token.isExpired(expiry.plusSeconds(1))).isTrue();
    }

    @Test
    void consumeMarksUsedAndBlocksReuse() {
        var now = Instant.now();
        var token = AccountToken.invitation(userId, "hash", now.plus(Duration.ofHours(1)));

        token.consume(now);
        assertThat(token.usedAt()).isEqualTo(now);
        assertThatThrownBy(() -> token.consume(now)).isInstanceOf(IllegalStateException.class);
    }

    @Test
    void consumeRejectsExpiredToken() {
        var expiry = Instant.now();
        var token = AccountToken.invitation(userId, "hash", expiry);

        assertThatThrownBy(() -> token.consume(expiry)).isInstanceOf(IllegalStateException.class);
    }

    @Test
    void requiresNonBlankHash() {
        assertThatThrownBy(() -> AccountToken.invitation(userId, " ", Instant.now()))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
