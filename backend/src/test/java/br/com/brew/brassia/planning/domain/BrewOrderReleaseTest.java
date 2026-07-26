package br.com.brew.brassia.planning.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class BrewOrderReleaseTest {

    private static final UUID BREWERY = UUID.randomUUID();
    private static final UUID RECIPE = UUID.randomUUID();
    private static final UUID USER = UUID.randomUUID();
    private static final Instant AT = Instant.parse("2026-09-01T10:00:00Z");

    private static BrewOrder draft() {
        var snapshot = new OrderSnapshot(
                new OrderSnapshot.Recipe(RECIPE, 1, "IPA", new BigDecimal("1.050"), new BigDecimal("1.010"),
                        new BigDecimal("5.2"), new BigDecimal("40"), new BigDecimal("12")),
                new OrderSnapshot.Equipment(UUID.randomUUID(), new BigDecimal("500"), new BigDecimal("20"),
                        new BigDecimal("72"), new BigDecimal("8")));
        return BrewOrder.create(BREWERY, "OP-2026-0001", RECIPE, 1, new BigDecimal("400"), snapshot);
    }

    @Test
    void releasesDraftWithResponsible() {
        var released = draft().release(USER, AT);

        assertThat(released.status()).isEqualTo(BrewOrderStatus.RELEASED);
        assertThat(released.assignedUserId()).isEqualTo(USER);
        assertThat(released.releasedAt()).isEqualTo(AT);
        assertThat(released.version()).isEqualTo(2);
    }

    @Test
    void rejectsReleaseWhenNotDraft() {
        var released = draft().release(USER, AT);
        assertThat(released.releasable()).isFalse();
        assertThatThrownBy(() -> released.release(USER, AT))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void rejectsReleaseWithoutResponsible() {
        assertThatThrownBy(() -> draft().release(null, AT))
                .isInstanceOf(NullPointerException.class);
    }
}
