package br.com.brew.brassia.equipment.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Duration;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class EquipmentMaintenanceTest {

    private static final UUID BREWERY = UUID.randomUUID();
    private static final UUID EQUIPMENT = UUID.randomUUID();
    private static final Instant T0 = Instant.parse("2026-08-01T08:00:00Z");

    private TimeRange range(long fromHour, long toHour) {
        return new TimeRange(T0.plus(Duration.ofHours(fromHour)), T0.plus(Duration.ofHours(toHour)));
    }

    @Test
    void schedulesMaintenanceWithoutInstrument() {
        var m = EquipmentMaintenance.schedule(BREWERY, EQUIPMENT, MaintenanceKind.MAINTENANCE, null,
                range(0, 4), "troca de vedação");

        assertThat(m.status()).isEqualTo(MaintenanceStatus.SCHEDULED);
        assertThat(m.instrument()).isNull();
        assertThat(m.version()).isZero();
    }

    @Test
    void calibrationRequiresInstrument() {
        assertThatThrownBy(() -> EquipmentMaintenance.schedule(BREWERY, EQUIPMENT, MaintenanceKind.CALIBRATION,
                "  ", range(0, 1), null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("instrumento");
    }

    @Test
    void rejectsEndNotAfterStart() {
        assertThatThrownBy(() -> new TimeRange(T0, T0)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new TimeRange(T0.plusSeconds(10), T0)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void detectsOverlapHalfOpen() {
        assertThat(range(0, 4).overlaps(range(3, 6))).isTrue();
        assertThat(range(0, 4).overlaps(range(4, 6))).isFalse(); // encosta, não sobrepõe
        assertThat(range(0, 4).overlaps(range(5, 6))).isFalse();
    }

    @Test
    void cancelIsOnlyValidWhenScheduled() {
        var m = EquipmentMaintenance.schedule(BREWERY, EQUIPMENT, MaintenanceKind.CALIBRATION, "pHmetro-01",
                range(0, 2), null);
        m.cancel();
        assertThat(m.status()).isEqualTo(MaintenanceStatus.CANCELLED);
        assertThatThrownBy(m::cancel).isInstanceOf(IllegalStateException.class);
    }
}
