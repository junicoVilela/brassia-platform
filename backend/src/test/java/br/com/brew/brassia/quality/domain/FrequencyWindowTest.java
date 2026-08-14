package br.com.brew.brassia.quality.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

import java.time.Duration;
import java.time.Instant;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** A janela de cadência de um ponto de controle (QLT-001-A). */
class FrequencyWindowTest {

    private static final Instant INICIO = Instant.parse("2026-08-14T08:00:00Z");

    @Test
    @DisplayName("SÓ A CADÊNCIA POR HORAS É JULGÁVEL POR RELÓGIO")
    void apenasPorHoras() {
        // As outras exigiriam inventar o significado delas — turno sem calendário de turnos, "por lote"
        // sem o lote ter acabado —, que é o oposto do que o débito pedia.
        assertThat(FrequencyWindow.of("PER_HOURS", 4)).isPresent();
        assertThat(FrequencyWindow.of("PER_BATCH", null)).isEmpty();
        assertThat(FrequencyWindow.of("PER_SHIFT", null)).isEmpty();
        assertThat(FrequencyWindow.of("PER_PACKAGING_RUN", null)).isEmpty();
        assertThat(FrequencyWindow.of("PER_HOURS", null)).isEmpty();
    }

    @Test
    @DisplayName("sem medição nenhuma, a contagem parte do início do lote")
    void semMedicaoContaDoInicio() {
        // Um lote de 30 horas sem a primeira medição está tão atrasado quanto um que mediu e parou.
        var janela = new FrequencyWindow(Duration.ofHours(4));

        assertThat(janela.dueAfter(null, INICIO)).isEqualTo(INICIO.plus(Duration.ofHours(4)));
        assertThat(janela.isLate(null, INICIO, INICIO.plus(Duration.ofHours(5)))).isTrue();
    }

    @Test
    @DisplayName("com medição, a contagem parte dela")
    void comMedicaoContaDaUltima() {
        var janela = new FrequencyWindow(Duration.ofHours(4));
        var medida = INICIO.plus(Duration.ofHours(3));

        assertThat(janela.isLate(medida, INICIO, INICIO.plus(Duration.ofHours(6)))).isFalse();
        assertThat(janela.isLate(medida, INICIO, INICIO.plus(Duration.ofHours(8)))).isTrue();
    }

    @Test
    @DisplayName("NO INSTANTE EXATO DO PRAZO AINDA ESTÁ NO PRAZO")
    void empateNaoAtrasa() {
        // Contar o empate como atraso avisaria a cervejaria no segundo em que ela ainda podia medir.
        var janela = new FrequencyWindow(Duration.ofHours(4));

        assertThat(janela.isLate(null, INICIO, INICIO.plus(Duration.ofHours(4)))).isFalse();
    }

    @Test
    @DisplayName("intervalo não positivo é recusado")
    void intervaloPositivo() {
        assertThatIllegalArgumentException().isThrownBy(() -> new FrequencyWindow(Duration.ZERO));
        assertThatIllegalArgumentException().isThrownBy(() -> new FrequencyWindow(Duration.ofHours(-1)));
    }
}
