package br.com.brew.brassia.inventory.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class StockLotPropertyTest {

    private static StockLotProperty property(String name, String unit) {
        return StockLotProperty.record(UUID.randomUUID(), UUID.randomUUID(), name, new BigDecimal("12.5"), unit,
                LotPropertySource.MANUAL, LotPropertyConfidence.HIGH, Instant.now(), UUID.randomUUID());
    }

    @Test
    void recordsValidValue() {
        var p = property("alfaAcido", "%");
        assertThat(p.property()).isEqualTo("alfaAcido");
        assertThat(p.measuredValue()).isEqualByComparingTo("12.5");
        assertThat(p.unit()).isEqualTo("%");
        assertThat(p.source()).isEqualTo(LotPropertySource.MANUAL);
    }

    @Test
    void normalizesBlankUnitToNull() {
        assertThat(property("celulas", "   ").unit()).isNull();
    }

    @Test
    void rejectsBlankProperty() {
        assertThatThrownBy(() -> property("  ", "%")).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void rejectsTooLongProperty() {
        assertThatThrownBy(() -> property("x".repeat(61), "%")).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void parsesSourceAndConfidenceCaseInsensitively() {
        assertThat(LotPropertySource.of("imported")).isEqualTo(LotPropertySource.IMPORTED);
        assertThat(LotPropertyConfidence.of(" low ")).isEqualTo(LotPropertyConfidence.LOW);
        assertThatThrownBy(() -> LotPropertySource.of("x")).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> LotPropertyConfidence.of("")).isInstanceOf(IllegalArgumentException.class);
    }
}
