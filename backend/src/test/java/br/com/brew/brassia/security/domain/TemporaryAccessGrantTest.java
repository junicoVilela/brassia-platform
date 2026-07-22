package br.com.brew.brassia.security.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import br.com.brew.brassia.security.domain.TemporaryAccessGrant.Status;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class TemporaryAccessGrantTest {

    private final Instant now = Instant.parse("2026-07-21T12:00:00Z");
    private final UUID requester = UUID.randomUUID();
    private final UUID other = UUID.randomUUID();

    private TemporaryAccessGrant grant(boolean critical, UUID approvedBy, Instant validUntil, Instant revokedAt) {
        return new TemporaryAccessGrant(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(),
                "some.perm", critical, "porque sim", now.minusSeconds(60), validUntil, requester, approvedBy, revokedAt);
    }

    @Test
    void commonGrantIsEffectiveWithinWindowWithoutApproval() {
        var g = grant(false, null, now.plusSeconds(3600), null);
        assertThat(g.isEffectiveNow(now)).isTrue();
        assertThat(g.statusAt(now)).isEqualTo(Status.ACTIVE);
    }

    @Test
    void criticalGrantIsNotEffectiveUntilApproved() {
        var pending = grant(true, null, now.plusSeconds(3600), null);
        assertThat(pending.isEffectiveNow(now)).isFalse();
        assertThat(pending.statusAt(now)).isEqualTo(Status.PENDING);

        var approved = grant(true, other, now.plusSeconds(3600), null);
        assertThat(approved.isEffectiveNow(now)).isTrue();
        assertThat(approved.statusAt(now)).isEqualTo(Status.ACTIVE);
    }

    @Test
    void expiredGrantIsNotEffective() {
        var g = grant(false, null, now.minusSeconds(1), null);
        assertThat(g.isEffectiveNow(now)).isFalse();
        assertThat(g.statusAt(now)).isEqualTo(Status.EXPIRED);
    }

    @Test
    void revokedGrantIsNotEffective() {
        var g = grant(false, null, now.plusSeconds(3600), now.minusSeconds(10));
        assertThat(g.isEffectiveNow(now)).isFalse();
        assertThat(g.statusAt(now)).isEqualTo(Status.REVOKED);
    }

    @Test
    void requesterCannotApproveOwnGrant() {
        var g = grant(true, null, now.plusSeconds(3600), null);
        assertThat(g.canApprove(requester, now)).isFalse();
        assertThat(g.canApprove(other, now)).isTrue();
    }

    @Test
    void approveBySecondUserReturnsApprovedGrant() {
        var g = grant(true, null, now.plusSeconds(3600), null);
        var approved = g.approve(other, now);
        assertThat(approved.isApproved()).isTrue();
        assertThat(approved.approvedBy()).isEqualTo(other);
    }

    @Test
    void approveByRequesterIsForbidden() {
        var g = grant(true, null, now.plusSeconds(3600), null);
        assertThatThrownBy(() -> g.approve(requester, now))
                .isInstanceOf(br.com.brew.brassia.shared.security.ForbiddenException.class);
    }

    @Test
    void approveAlreadyApprovedConflicts() {
        var g = grant(true, other, now.plusSeconds(3600), null);
        assertThatThrownBy(() -> g.approve(UUID.randomUUID(), now))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void revokeMarksRevokedAt() {
        var g = grant(false, null, now.plusSeconds(3600), null);
        var revoked = g.revoke(now);
        assertThat(revoked.isRevoked()).isTrue();
        assertThat(revoked.revokedAt()).isEqualTo(now);
    }

    @Test
    void revokeTwiceConflicts() {
        var g = grant(false, null, now.plusSeconds(3600), now.minusSeconds(1));
        assertThatThrownBy(() -> g.revoke(now)).isInstanceOf(IllegalStateException.class);
    }
}
