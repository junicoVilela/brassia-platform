package br.com.brew.brassia.inventory.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class StockMovementTest {

    private static final UUID B = UUID.randomUUID();
    private static final UUID LOT = UUID.randomUUID();
    private static final UUID ING = UUID.randomUUID();
    private static final UUID ACTOR = UUID.randomUUID();
    private static final Instant AT = Instant.parse("2026-09-01T10:00:00Z");

    private static StockMovement of(StockMovementType type, String qty, String reason) {
        return StockMovement.record(B, LOT, ING, type, new BigDecimal(qty), null, reason, AT, ACTOR);
    }

    @Test
    void entryIncreasesOnHand() {
        var m = of(StockMovementType.ENTRY, "25", null);
        assertThat(m.onHandDelta()).isEqualByComparingTo("25");
        assertThat(m.reservedDelta()).isEqualByComparingTo("0");
    }

    @Test
    void consumptionDecreasesOnHand() {
        assertThat(of(StockMovementType.CONSUMPTION, "10", null).onHandDelta()).isEqualByComparingTo("-10");
        assertThat(of(StockMovementType.LOSS, "3", null).onHandDelta()).isEqualByComparingTo("-3");
    }

    @Test
    void reservationAffectsReservedOnly() {
        var r = of(StockMovementType.RESERVATION, "5", null);
        assertThat(r.onHandDelta()).isEqualByComparingTo("0");
        assertThat(r.reservedDelta()).isEqualByComparingTo("5");
        assertThat(of(StockMovementType.RELEASE, "5", null).reservedDelta()).isEqualByComparingTo("-5");
    }

    @Test
    void availableIsOnHandMinusReserved() {
        var balance = new StockBalance(new BigDecimal("25"), new BigDecimal("10"));
        assertThat(balance.available()).isEqualByComparingTo("15");
    }

    @Test
    void rejectsNonPositiveQuantity() {
        assertThatThrownBy(() -> of(StockMovementType.ENTRY, "0", null))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void adjustmentRequiresReason() {
        assertThatThrownBy(() -> of(StockMovementType.ADJUSTMENT_OUT, "2", "  "))
                .isInstanceOf(IllegalArgumentException.class);
        assertThat(of(StockMovementType.ADJUSTMENT_IN, "2", "recontagem").reason()).isEqualTo("recontagem");
    }
}
