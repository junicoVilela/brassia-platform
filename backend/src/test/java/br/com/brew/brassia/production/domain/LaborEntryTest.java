package br.com.brew.brassia.production.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** Apontamento de horas trabalhadas (CST-001-A). */
class LaborEntryTest {

    private static final UUID CERVEJARIA = UUID.randomUUID();
    private static final UUID LOTE = UUID.randomUUID();
    private static final UUID ATOR = UUID.randomUUID();
    private static final Instant INICIO = Instant.parse("2026-08-14T06:00:00Z");

    @Test
    @DisplayName("DUAS PESSOAS POR TRÊS HORAS SÃO SEIS HORAS-HOMEM")
    void horasHomemMultiplicamPelasPessoas() {
        // Guardar "3 h" perderia exatamente a metade que a cervejaria paga.
        var entry = LaborEntry.record(CERVEJARIA, LOTE, "Brassa", INICIO, INICIO.plusSeconds(3 * 3600), 2,
                ATOR, INICIO);

        assertThat(entry.manHours()).isEqualByComparingTo("6");
    }

    @Test
    @DisplayName("meia hora conta como meia hora")
    void fracaoDeHora() {
        var entry = LaborEntry.record(CERVEJARIA, LOTE, "Limpeza", INICIO, INICIO.plusSeconds(1800), 1,
                ATOR, INICIO);

        assertThat(entry.manHours()).isEqualByComparingTo("0.5");
    }

    @Test
    @DisplayName("PERÍODO QUE NÃO ANDA É RECUSADO")
    void periodoPrecisaAndar() {
        // Seria custo de mão de obra saindo de um apontamento que descreve ninguém trabalhando.
        assertThatIllegalArgumentException().isThrownBy(() -> LaborEntry.record(CERVEJARIA, LOTE, "Brassa",
                INICIO, INICIO, 1, ATOR, INICIO));
        assertThatIllegalArgumentException().isThrownBy(() -> LaborEntry.record(CERVEJARIA, LOTE, "Brassa",
                INICIO, INICIO.minusSeconds(60), 1, ATOR, INICIO));
    }

    @Test
    @DisplayName("apontamento sem gente e sem atividade é recusado")
    void exigeGenteEAtividade() {
        assertThatIllegalArgumentException().isThrownBy(() -> LaborEntry.record(CERVEJARIA, LOTE, "Brassa",
                INICIO, INICIO.plusSeconds(3600), 0, ATOR, INICIO));
        assertThatIllegalArgumentException().isThrownBy(() -> LaborEntry.record(CERVEJARIA, LOTE, "   ",
                INICIO, INICIO.plusSeconds(3600), 1, ATOR, INICIO));
    }
}
