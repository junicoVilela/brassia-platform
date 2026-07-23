package br.com.brew.brassia.water.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class WaterReportTest {

    private static final UUID BREWERY = UUID.randomUUID();

    private IonProfile ions(String calcium) {
        return new IonProfile(new BigDecimal(calcium), BigDecimal.ZERO, BigDecimal.ZERO,
                new BigDecimal("50"), new BigDecimal("30"), new BigDecimal("120"));
    }

    private WaterReport report(LocalDate collectedOn, IonProfile ions) {
        return WaterReport.record(BREWERY, WaterSourceId.newId(), collectedOn, WaterMethod.LAB, ions, "poço");
    }

    @Test
    void recordsValidReport() {
        var report = report(LocalDate.now().minusDays(3), ions("40"));

        assertThat(report.method()).isEqualTo(WaterMethod.LAB);
        assertThat(report.ions().calcium()).isEqualByComparingTo("40");
        assertThat(report.notes()).isEqualTo("poço");
    }

    @Test
    void rejectsNegativeOrOutOfRangeIon() {
        assertThatThrownBy(() -> ions("-1")).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> ions("20000")).isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("mg/L");
    }

    @Test
    void rejectsFutureCollectionDate() {
        assertThatThrownBy(() -> report(LocalDate.now().plusDays(1), ions("40")))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("futura");
    }

    @Test
    void rejectsInvalidMethod() {
        assertThatThrownBy(() -> WaterMethod.of("GUESS")).isInstanceOf(IllegalArgumentException.class);
    }
}
