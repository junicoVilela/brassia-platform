package br.com.brew.brassia.quality.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

class FrequencyTest {

    @Test
    void frequenciaPorHorasExigeIntervaloPositivo() {
        assertThatThrownBy(() -> new Frequency(FrequencyKind.PER_HOURS, null))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new Frequency(FrequencyKind.PER_HOURS, 0))
                .isInstanceOf(IllegalArgumentException.class);

        assertThat(new Frequency(FrequencyKind.PER_HOURS, 4).describe()).isEqualTo("A cada 4 horas");
    }

    @Test
    void soAFrequenciaPorHorasAceitaIntervalo() {
        // "A cada lote, de 4 em 4 horas" não descreve cadência nenhuma.
        assertThatThrownBy(() -> new Frequency(FrequencyKind.PER_BATCH, 4))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("só a frequência por horas");
    }

    @Test
    void descreveAsDemaisPeloRotulo() {
        assertThat(Frequency.perBatch().describe()).isEqualTo("A cada lote");
        assertThat(new Frequency(FrequencyKind.PER_SHIFT, null).describe()).isEqualTo("A cada turno");
    }
}
