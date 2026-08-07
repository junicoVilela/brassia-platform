package br.com.brew.brassia.utilities.application.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import br.com.brew.brassia.utilities.PackagedVolumeSource;
import br.com.brew.brassia.utilities.UtilityReadingSource;
import br.com.brew.brassia.utilities.UtilityReadingSource.Coverage;
import br.com.brew.brassia.utilities.UtilityReadingSource.Reading;
import br.com.brew.brassia.utilities.UtilityReadingSource.UtilityType;
import br.com.brew.brassia.utilities.application.port.inbound.UtilityQueries;
import br.com.brew.brassia.utilities.domain.UtilityIndicator;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** Como o relatório junta fontes que não se conhecem (UTL-001). */
class UtilityQueryHandlerTest {

    private static final UUID BREWERY = UUID.randomUUID();
    private static final Instant FROM = Instant.parse("2026-07-01T00:00:00Z");
    private static final Instant TO = Instant.parse("2026-08-01T00:00:00Z");

    @Test
    @DisplayName("cada fonte responde pelo que mede, e o relatório soma por utilidade")
    void juntaAsFontes() {
        var handler = handler(BigDecimal.valueOf(2000),
                source(List.of(reading(UtilityType.WATER, 3000), reading(UtilityType.ENERGY, 120)),
                        List.of()),
                source(List.of(reading(UtilityType.WATER, 1000)), List.of()));

        var report = handler.report(BREWERY, FROM, TO);

        assertThat(report.packagedLiters()).isEqualByComparingTo("2000");
        assertThat(of(report, UtilityType.WATER).total()).isEqualByComparingTo("4000");
        assertThat(of(report, UtilityType.WATER).perLiter()).isEqualByComparingTo("2.0000");
        assertThat(of(report, UtilityType.ENERGY).total()).isEqualByComparingTo("120");
    }

    @Test
    @DisplayName("utilidade que ninguém mediu não entra no relatório zerada")
    void naoInventaUtilidadeNaoMedida() {
        var handler = handler(BigDecimal.valueOf(2000),
                source(List.of(reading(UtilityType.WATER, 3000)), List.of()));

        var types = handler.report(BREWERY, FROM, TO).indicators().stream()
                .map(UtilityIndicator::type)
                .toList();

        assertThat(types).containsExactly(UtilityType.WATER);
    }

    @Test
    @DisplayName("a cobertura declarada por uma fonte não contamina o que ela não mede")
    void coberturaFicaComQuemADeclarou() {
        var limpeza = source(List.of(reading(UtilityType.WATER, 3000)),
                List.of(new Coverage("ciclos de limpeza", 12, 12)));
        var gas = source(List.of(reading(UtilityType.CO2, 40)), List.of());
        var handler = handler(BigDecimal.valueOf(2000), limpeza, gas);

        var report = handler.report(BREWERY, FROM, TO);

        assertThat(of(report, UtilityType.WATER).fullyMeasured()).isTrue();
        // O gás não declarou cobertura (UTL-001-A): o CO₂ não pode se dizer completo de carona.
        assertThat(of(report, UtilityType.CO2).fullyMeasured()).isFalse();
    }

    @Test
    @DisplayName("período invertido é erro de quem perguntou, não relatório vazio")
    void recusaPeriodoInvertido() {
        var handler = handler(BigDecimal.ZERO, source(List.of(), List.of()));

        assertThatThrownBy(() -> handler.report(BREWERY, TO, FROM))
                .isInstanceOf(IllegalArgumentException.class);
    }

    private static UtilityIndicator of(UtilityQueries.Report report, UtilityType type) {
        return report.indicators().stream()
                .filter(indicator -> indicator.type() == type)
                .findFirst()
                .orElseThrow(() -> new AssertionError("sem indicador de " + type));
    }

    private static UtilityQueryHandler handler(BigDecimal liters, UtilityReadingSource... sources) {
        PackagedVolumeSource packaged = (brewery, from, to) -> liters;
        return new UtilityQueryHandler(List.of(sources), packaged);
    }

    private static UtilityReadingSource source(List<Reading> readings, List<Coverage> coverage) {
        return new UtilityReadingSource() {
            @Override
            public List<Reading> readingsIn(UUID breweryId, Instant from, Instant to) {
                return readings;
            }

            @Override
            public List<Coverage> coverageIn(UUID breweryId, Instant from, Instant to) {
                return coverage;
            }
        };
    }

    private static Reading reading(UtilityType type, int amount) {
        return new Reading(type, BigDecimal.valueOf(amount), FROM, "medidor de " + type, true);
    }
}
