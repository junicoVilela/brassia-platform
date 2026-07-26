package br.com.brew.brassia.planning.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class BrewOrderCancelTest {

    private static final UUID BREWERY = UUID.randomUUID();
    private static final UUID RECIPE = UUID.randomUUID();
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
    void cancelsDraftWithReason() {
        var cancelled = draft().cancel("mudança de plano", AT);

        assertThat(cancelled.status()).isEqualTo(BrewOrderStatus.CANCELLED);
        assertThat(cancelled.cancelReason()).isEqualTo("mudança de plano");
        assertThat(cancelled.cancelledAt()).isEqualTo(AT);
        assertThat(cancelled.version()).isEqualTo(2);
    }

    @Test
    void cancelsReleasedOrder() {
        var released = draft().release(UUID.randomUUID(), AT);
        var cancelled = released.cancel("erro de programação", AT);
        assertThat(cancelled.status()).isEqualTo(BrewOrderStatus.CANCELLED);
    }

    @Test
    void rejectsBlankReason() {
        assertThatThrownBy(() -> draft().cancel("  ", AT))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void rejectsCancellingNonCancellable() {
        var cancelled = draft().cancel("motivo", AT);
        assertThat(cancelled.cancellable()).isFalse();
        assertThatThrownBy(() -> cancelled.cancel("de novo", AT))
                .isInstanceOf(IllegalStateException.class);
    }
}
