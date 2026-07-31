package br.com.brew.brassia.fermentation.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class FermentationReadingTest {

    private static final UUID BREWERY = UUID.randomUUID();
    private static final UUID BATCH = UUID.randomUUID();
    private static final Instant AT = Instant.parse("2026-07-31T10:00:00Z");

    private static FermentationReading record(ReadingKind kind, String value, String unit) {
        return FermentationReading.record(BREWERY, BATCH, kind, ReadingSource.MANUAL, new BigDecimal(value), unit, AT);
    }

    @Test
    void recordsPlausibleReadingAsValid() {
        var reading = record(ReadingKind.DENSITY, "1.048", "sg");

        assertThat(reading.valid()).isTrue();
        assertThat(reading.invalidReason()).isNull();
        assertThat(reading.unit()).isEqualTo("SG");
        assertThat(reading.value()).isEqualByComparingTo("1.048");
    }

    @Test
    void flagsImplausibleReadingInsteadOfRejecting() {
        var reading = record(ReadingKind.TEMPERATURE, "150", "C");

        assertThat(reading.valid()).isFalse();
        assertThat(reading.invalidReason()).contains("TEMPERATURE", "150", "C");
        assertThat(reading.value()).isEqualByComparingTo("150");
    }

    @Test
    void acceptsBoundaryValues() {
        assertThat(record(ReadingKind.PH, "2.5", "PH").valid()).isTrue();
        assertThat(record(ReadingKind.PH, "7.5", "PH").valid()).isTrue();
        assertThat(record(ReadingKind.PH, "7.51", "PH").valid()).isFalse();
        assertThat(record(ReadingKind.PRESSURE, "0", "BAR").valid()).isTrue();
    }

    @Test
    void appliesRangeOfTheInformedUnit() {
        // 40 °C é plausível; os mesmos 40 em Fahrenheit ficariam abaixo da faixa.
        assertThat(record(ReadingKind.TEMPERATURE, "40", "C").valid()).isTrue();
        assertThat(record(ReadingKind.TEMPERATURE, "10", "F").valid()).isFalse();
    }

    @Test
    void rejectsUnitIncompatibleWithKind() {
        assertThatThrownBy(() -> record(ReadingKind.DENSITY, "1.048", "C"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("incompatível");
    }

    @Test
    void rejectsBlankOrUnknownUnit() {
        assertThatThrownBy(() -> record(ReadingKind.PH, "4.2", " "))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> record(ReadingKind.PRESSURE, "1", "KPA"))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void parsesKindAndSourceCaseInsensitively() {
        assertThat(ReadingKind.of(" density ")).isEqualTo(ReadingKind.DENSITY);
        assertThat(ReadingSource.of("sensor")).isEqualTo(ReadingSource.SENSOR);
        assertThatThrownBy(() -> ReadingKind.of("COLOR")).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> ReadingSource.of(null)).isInstanceOf(IllegalArgumentException.class);
    }
}
