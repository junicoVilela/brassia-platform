package br.com.brew.brassia.production.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class MeasurementTest {

    private static Measurement record(MeasurementKind kind, String unit) {
        return Measurement.record(UUID.randomUUID(), UUID.randomUUID(), null, kind, new BigDecimal("1.048"), unit,
                null, "densímetro", MeasurementSource.MANUAL, Instant.now(), UUID.randomUUID());
    }

    @Test
    void recordsAndNormalizesUnit() {
        var m = record(MeasurementKind.DENSITY, "sg");
        assertThat(m.kind()).isEqualTo(MeasurementKind.DENSITY);
        assertThat(m.unit()).isEqualTo("SG");
        assertThat(m.source()).isEqualTo(MeasurementSource.MANUAL);
    }

    @Test
    void rejectsUnitIncompatibleWithKind() {
        assertThatThrownBy(() -> record(MeasurementKind.DENSITY, "C"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("incompatível");
    }

    @Test
    void parsesKindAndSourceCaseInsensitively() {
        assertThat(MeasurementKind.of("temperature")).isEqualTo(MeasurementKind.TEMPERATURE);
        assertThat(MeasurementSource.of(" device ")).isEqualTo(MeasurementSource.DEVICE);
        assertThatThrownBy(() -> MeasurementKind.of("weight")).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> MeasurementSource.of("robot")).isInstanceOf(IllegalArgumentException.class);
    }
}
