package br.com.brew.brassia.inventory.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class StockLotTest {

    private static final UUID BREWERY = UUID.randomUUID();
    private static final UUID INGREDIENT = UUID.randomUUID();
    private static final UUID SUPPLIER = UUID.randomUUID();
    private static final Instant AT = Instant.parse("2026-09-01T10:00:00Z");
    private static final LocalDate EXPIRY = LocalDate.parse("2027-09-01");

    private static StockLot receive(BigDecimal qty, BigDecimal cost, StockInspection inspection) {
        return StockLot.receive(BREWERY, INGREDIENT, SUPPLIER, "L-42", qty, StockUnit.KG, cost, EXPIRY, AT, inspection);
    }

    @Test
    void receivesApprovedAsAvailable() {
        var lot = receive(new BigDecimal("25"), new BigDecimal("4.50"), StockInspection.APPROVED);
        assertThat(lot.id()).isNotNull();
        assertThat(lot.available()).isTrue();
        assertThat(lot.receivedQuantity()).isEqualByComparingTo("25");
        assertThat(lot.unit()).isEqualTo(StockUnit.KG);
        assertThat(lot.supplierLotCode()).isEqualTo("L-42");
    }

    @Test
    void blockedLotIsNotAvailable() {
        var lot = receive(new BigDecimal("25"), new BigDecimal("4.50"), StockInspection.BLOCKED);
        assertThat(lot.available()).isFalse();
    }

    @Test
    void rejectsNonPositiveQuantity() {
        assertThatThrownBy(() -> receive(BigDecimal.ZERO, new BigDecimal("1"), StockInspection.APPROVED))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void rejectsNegativeCost() {
        assertThatThrownBy(() -> receive(new BigDecimal("10"), new BigDecimal("-1"), StockInspection.APPROVED))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void rejectsUnknownUnit() {
        assertThatThrownBy(() -> StockUnit.of("SACK")).isInstanceOf(IllegalArgumentException.class);
    }
}
