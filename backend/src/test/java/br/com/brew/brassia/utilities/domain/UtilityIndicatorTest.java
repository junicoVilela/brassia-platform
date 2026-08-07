package br.com.brew.brassia.utilities.domain;

import static org.assertj.core.api.Assertions.assertThat;

import br.com.brew.brassia.utilities.UtilityReadingSource.Coverage;
import br.com.brew.brassia.utilities.UtilityReadingSource.Reading;
import br.com.brew.brassia.utilities.UtilityReadingSource.UtilityType;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** O que o indicador divide, o que ele se recusa a somar e quando ele se cala (UTL-001). */
class UtilityIndicatorTest {

    private static final Instant NOW = Instant.parse("2026-08-07T10:00:00Z");

    @Test
    @DisplayName("o consumo do período dividido pelo envasado é o indicador")
    void dividePeloEnvasado() {
        var indicator = UtilityIndicator.of(UtilityType.WATER,
                List.of(measured(300), measured(150)), BigDecimal.valueOf(150), List.of());

        assertThat(indicator.total()).isEqualByComparingTo("450");
        assertThat(indicator.perLiter()).isEqualByComparingTo("3.0000");
        assertThat(indicator.unit()).isEqualTo("L");
    }

    @Test
    @DisplayName("medido e estimado viajam separados, e o por litro medido é o que vai a auditoria")
    void separaMedidoDeEstimado() {
        var indicator = UtilityIndicator.of(UtilityType.WATER,
                List.of(measured(300), estimated(150)), BigDecimal.valueOf(150), List.of());

        assertThat(indicator.measured()).isEqualByComparingTo("300");
        assertThat(indicator.estimated()).isEqualByComparingTo("150");
        assertThat(indicator.measuredPerLiter()).isEqualByComparingTo("2.0000");
        // O total geral continua existindo para quem quiser somar os dois sabendo o que está fazendo.
        assertThat(indicator.perLiter()).isEqualByComparingTo("3.0000");
    }

    @Test
    @DisplayName("período sem envase não tem indicador — e não tem zero")
    void semEnvaseNaoHaIndicador() {
        var indicator = UtilityIndicator.of(UtilityType.WATER, List.of(measured(500)),
                BigDecimal.ZERO, List.of());

        assertThat(indicator.perLiter()).isNull();
        assertThat(indicator.measuredPerLiter()).isNull();
        // O consumo aconteceu e continua aparecendo: a fábrica gastou água sem produzir cerveja.
        assertThat(indicator.total()).isEqualByComparingTo("500");
    }

    @Test
    @DisplayName("sem cobertura declarada, o indicador não afirma que mediu tudo")
    void semCoberturaNaoAfirmaNada() {
        var indicator = UtilityIndicator.of(UtilityType.CO2, List.of(measured(12)),
                BigDecimal.valueOf(1000), List.of());

        assertThat(indicator.fullyMeasured()).isFalse();
    }

    @Test
    @DisplayName("cobertura parcial derruba a medição completa; cobertura cheia a sustenta")
    void coberturaDecideAMedicaoCompleta() {
        var parcial = UtilityIndicator.of(UtilityType.WATER, List.of(measured(300)),
                BigDecimal.valueOf(150), List.of(new Coverage("ciclos de limpeza", 8, 12)));
        var cheia = UtilityIndicator.of(UtilityType.WATER, List.of(measured(300)),
                BigDecimal.valueOf(150), List.of(new Coverage("ciclos de limpeza", 12, 12)));

        assertThat(parcial.fullyMeasured()).isFalse();
        assertThat(cheia.fullyMeasured()).isTrue();
    }

    @Test
    @DisplayName("as fontes do número vêm junto, sem repetição, para o indicador ser rastreável")
    void listaAsFontes() {
        var indicator = UtilityIndicator.of(UtilityType.ENERGY,
                List.of(reading(10, "ciclo CIP-1", true), reading(10, "ciclo CIP-1", true),
                        reading(5, "ciclo CIP-2", true)),
                BigDecimal.valueOf(100), List.of());

        assertThat(indicator.sources()).containsExactly("ciclo CIP-1", "ciclo CIP-2");
        assertThat(indicator.unit()).isEqualTo("kWh");
    }

    private static Reading measured(int amount) {
        return reading(amount, "medidor", true);
    }

    private static Reading estimated(int amount) {
        return reading(amount, "estimativa", false);
    }

    private static Reading reading(int amount, String source, boolean measured) {
        return new Reading(UtilityType.WATER, BigDecimal.valueOf(amount), NOW, source, measured);
    }
}
